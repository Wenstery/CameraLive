package com.view.cameralive;

import android.media.MediaCodec;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Created by Wenstery on 2/19/2017.
 */
public class RtmpWorker {
    private static final String TAG = "RtmpWorker";
    private int aChannel;
    private int aSamplerate;
    private byte[] aac_specific_config;
    private final Object dataQueueLocker = new Object();
    private final Object waitLocker = new Object();
    private Queue<FlvFrame> dataQueue = new ArrayDeque<FlvFrame>();

    private native boolean nativeRtmpInit(String rtmp_url);

    private native int nativeWriteVideoFrame(byte[] video_data, long pts, int SpsPpsFlag);

    private native int nativeWriteAudioFrame(byte[] audio_data, long pts);

    private native void nativeRtmpRelease();

    static {
        System.loadLibrary("crypto");
        System.loadLibrary("ssl");
        System.loadLibrary("rtmp");
        System.loadLibrary("rtmp_jni");
    }

    public RtmpWorker(int channels, int samplerate) {
        aChannel = channels;
        aSamplerate = samplerate;

    }

    public boolean startWorker(String rtmpUrl) {
        boolean res = nativeRtmpInit(rtmpUrl);
        if (res && (worker != null) && (!worker.isAlive())) {
            worker.start();
            Log.i(TAG, "publish worker start ");
        } else {
            Log.e(TAG, "failed to start publish worker");
        }
        return res;

    }

    public void stopWorker() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (worker != null) {
                    worker.interrupt();
                    try {
                        worker.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        worker.interrupt();
                    }
                    synchronized (dataQueueLocker) {
                        dataQueue.clear();
                    }
                    worker = null;
                    Log.i(TAG, "publish worker exit ok ");
                    nativeRtmpRelease();
                }
            }
        }).start();
    }

    private Thread worker = new Thread(new Runnable() {
        @Override
        public void run() {
            while (!Thread.interrupted()) {
                while (!dataQueue.isEmpty()) {
                    processAll();
                }
                synchronized (dataQueueLocker) {
                    try {
                        // isEmpty() may take some time, so we set timeout to detect next frame
                        dataQueueLocker.wait(100);
                    } catch (InterruptedException ie) {
                        worker.interrupt();
                    }
                }

            }
        }
    });


    public void putToQueue(FlvFrame frame) {
        if (worker != null && worker.isAlive()) {
            synchronized (dataQueueLocker) {
                dataQueue.add(frame);
                dataQueueLocker.notifyAll();
            }
        }
    }

    private void processAll() {
        while (true) {
            if (!(processOne())) {
                break;
            }
            synchronized (waitLocker) {
                try {
                    // wait before sending next frame
                    waitLocker.wait(25);
                } catch (InterruptedException ie) {
                    worker.interrupt();
                }
            }
        }
    }

    private FlvFrame pollOneFromDataQueue() {
        synchronized (dataQueueLocker) {
            return dataQueue.poll();
        }
    }

    private boolean processOne() {
        FlvFrame bd = pollOneFromDataQueue();
        if (bd != null) {
            process(bd);
        }
        return (bd != null);
    }

    private void process(FlvFrame frame) {
        int length = frame.data.limit() - frame.data.position();
        Log.d(TAG, "frame data size: " + length);
        int size;
        if (length > 0) {
            byte[] frameData = new byte[length];
            frame.data.get(frameData);
            frame.data.flip();
            if (frame.type == CodecFlvTag.Audio) {
                Log.d(TAG, "write audio data begin " + frameData.length);
                size = nativeWriteAudioFrame(frameData, frame.dts);
                Log.d(TAG, "write audio data: " + size);
            } else {
                Log.d(TAG, "write video data begin " + frameData.length);
                size = nativeWriteVideoFrame(frameData, frame.dts, frame.avc_aac_type);
                Log.d(TAG, "write video data: " + size);
            }
        }

    }

    public void onProcessAac(ByteBuffer buffer, MediaCodec.BufferInfo bufferInfo) {
        long dts = bufferInfo.presentationTimeUs;
        byte[] frameBytes = new byte[bufferInfo.size + 2];
        byte aac_packet_type = 1; // 1 = AAC raw
        if (aac_specific_config == null) {
            frameBytes = new byte[4];

            // @see aac-mp4a-format-ISO_IEC_14496-3+2001.pdf
            // AudioSpecificConfig (), page 33
            // 1.6.2.1 AudioSpecificConfig
            // audioObjectType; 5 bslbf
            byte ch = (byte) (buffer.get(0) & 0xf8);
            // 3bits left.

            // samplingFrequencyIndex; 4 bslbf
            byte samplingFrequencyIndex = 0x04;
            if (aSamplerate == CodecAudioSampleRate.R22050) {
                samplingFrequencyIndex = 0x07;
            } else if (aSamplerate == CodecAudioSampleRate.R11025) {
                samplingFrequencyIndex = 0x0a;
            }
            ch |= (samplingFrequencyIndex >> 1) & 0x07;
            frameBytes[2] = ch;

            ch = (byte) ((samplingFrequencyIndex << 7) & 0x80);
            // 7bits left.

            // channelConfiguration; 4 bslbf
            byte channelConfiguration = 1;
            if (aChannel == 2) {
                channelConfiguration = 2;
            }
            ch |= (channelConfiguration << 3) & 0x78;
            // 3bits left.

            // GASpecificConfig(), page 451
            // 4.4.1 Decoder configuration (GASpecificConfig)
            // frameLengthFlag; 1 bslbf
            // dependsOnCoreCoder; 1 bslbf
            // extensionFlag; 1 bslbf
            frameBytes[3] = ch;

            aac_specific_config = frameBytes;
            aac_packet_type = 0; // 0 = AAC sequence header
        } else {
            buffer.get(frameBytes, 2, frameBytes.length - 2);
        }

        byte sound_format = 10; // AAC
        byte sound_type = 0; // 0 = Mono sound
        if (aChannel == 2) {
            sound_type = 1; // 1 = Stereo sound
        }
        byte sound_size = 1; // 1 = 16-bit samples
        byte sound_rate = 3; // 44100, 22050, 11025
        if (aSamplerate == 22050) {
            sound_rate = 2;
        } else if (aSamplerate == 11025) {
            sound_rate = 1;
        }

        // for audio frame, there is 1 or 2 bytes header:
        //      1bytes, SoundFormat|SoundRate|SoundSize|SoundType
        //      1bytes, AACPacketType for SoundFormat == 10, 0 is sequence header.
        byte audio_header = (byte) (sound_type & 0x01);
        audio_header |= (sound_size << 1) & 0x02;
        audio_header |= (sound_rate << 2) & 0x0c;
        audio_header |= (sound_format << 4) & 0xf0;

        frameBytes[0] = audio_header;
        frameBytes[1] = aac_packet_type;

        FlvFrame frame = new FlvFrame();
        frame.data = ByteBuffer.wrap(frameBytes);
        frame.size = frameBytes.length;
        frame.type = CodecFlvTag.Audio;
        frame.dts = dts;
        frame.avc_aac_type = aac_packet_type;
        putToQueue(frame);

    }

    public void onProcessH264(final ByteBuffer bb, MediaCodec.BufferInfo bi) {
        long dts = bi.presentationTimeUs;
        int startOffset = 0;
        if (bb.get(0) == 0x00 && bb.get(1) == 0x00) {
            if (bb.get(2) == 0x01)
                startOffset = 3;
            else if (bb.get(2) == 0x00 && bb.get(3) == 0x01)
                startOffset = 4;
        }
        int naluType = bb.get(startOffset);
        if (naluType == AvcNaluType.SPS) {
            byte[] sps = new byte[bi.size - 2 * startOffset - 4];
            bb.position(startOffset);
            bb.get(sps, 0, sps.length);
            byte[] pps = new byte[4];
            bb.position(startOffset * 2 + sps.length);
            bb.get(pps, 0, 4);
            FlvFrame frame = new FlvFrame();
            frame.size = 16 + sps.length + pps.length;
            frame.data = ByteBuffer.allocate(frame.size);
            frame.data.put((byte) 0x17);
            frame.data.put((byte) 0x00);
            frame.data.put((byte) 0x00);
            frame.data.put((byte) 0x00);
            frame.data.put((byte) 0x00);

            frame.data.put((byte) 0x01);
            frame.data.put(sps[1]);
            frame.data.put(sps[2]);
            frame.data.put(sps[3]);
            frame.data.put((byte) 0xff);

            frame.data.put((byte) 0xe1);
            frame.data.putShort((short) sps.length);
            frame.data.put(sps);

            frame.data.put((byte) 0x01);
            frame.data.putShort((short) pps.length);
            frame.data.put(pps);
            frame.data.flip();

            frame.type = CodecFlvTag.Video;
            frame.frame_type = CodecVideoAVCFrame.KeyFrame;
            frame.avc_aac_type = CodecVideoAVCType.SequenceHeader;
            frame.dts = dts;
            putToQueue(frame);

        } else if (naluType == AvcNaluType.NonIDR || naluType == AvcNaluType.IDR) {
            byte[] videoData = new byte[bi.size - startOffset];
            bb.position(startOffset);
            bb.get(videoData, 0, videoData.length);
            FlvFrame frame = new FlvFrame();
            frame.data = ByteBuffer.allocate(videoData.length + 5);
            if (naluType == AvcNaluType.IDR)
                frame.frame_type = CodecVideoAVCFrame.KeyFrame;
            else
                frame.frame_type = CodecVideoAVCFrame.InterFrame;
            if (frame.frame_type == CodecVideoAVCFrame.KeyFrame) {
                frame.data.put((byte) 0x17);
                frame.data.put((byte) 0x01);
                frame.data.put((byte) 0x00);
                frame.data.put((byte) 0x00);
                frame.data.put((byte) 0x00);
            } else if (frame.frame_type == CodecVideoAVCFrame.InterFrame) {
                frame.data.put((byte) 0x27);
                frame.data.put((byte) 0x01);
                frame.data.put((byte) 0x00);
                frame.data.put((byte) 0x00);
                frame.data.put((byte) 0x00);
            }

            frame.data.put(videoData);
            frame.data.flip();
            frame.type = CodecFlvTag.Video;
            frame.avc_aac_type = CodecVideoAVCType.NALU;
            frame.dts = dts;
            Log.d(TAG,"put video data to queque!");
            putToQueue(frame);
        }
    }

    class FlvFrame {
        // the tag bytes.
        public ByteBuffer data;
        // the codec type for audio/aac and video/avc for instance.
        public int size;
        public int avc_aac_type;
        // the frame type, keyframe or not.
        public int frame_type;
        // the tag type, audio, video or data.
        public int type;
        // the dts in ms, tbn is 1000.
        public long dts;

        public boolean is_keyframe() {
            return is_video() && frame_type == CodecVideoAVCFrame.KeyFrame;
        }

        public boolean is_sequenceHeader() {
            return avc_aac_type == 0;
        }

        public boolean is_video() {
            return type == CodecFlvTag.Video;
        }

        public boolean is_audio() {
            return type == CodecFlvTag.Audio;
        }
    }

    class CodecFlvTag {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;

        // 8 = audio
        public final static int Audio = 8;
        // 9 = video
        public final static int Video = 9;
        // 18 = script data
        public final static int Script = 18;
    }

    ;

    class CodecVideoAVCFrame {
        // set to the zero to reserved, for array map.
        public final static int Reserved = 0;
        public final static int Reserved1 = 6;

        public final static int KeyFrame = 1;
        public final static int InterFrame = 2;
        public final static int DisposableInterFrame = 3;
        public final static int GeneratedKeyFrame = 4;
        public final static int VideoInfoFrame = 5;
    }

    class CodecVideoAVCType {
        // set to the max value to reserved, for array map.
        public final static int Reserved = 3;

        public final static int SequenceHeader = 0;
        public final static int NALU = 1;
        public final static int SequenceHeaderEOF = 2;
    }

    class CodecAudioSampleRate {
        // set to the max value to reserved, for array map.
        public final static int Reserved = 4;

        public final static int R5512 = 0;
        public final static int R11025 = 1;
        public final static int R22050 = 2;
        public final static int R44100 = 3;
    }


    class AvcNaluType {
        // Unspecified
        public final static int Reserved = 0;

        // Coded slice of a non-IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int NonIDR = 1;
        // Coded slice data partition A slice_data_partition_a_layer_rbsp( )
        public final static int DataPartitionA = 2;
        // Coded slice data partition B slice_data_partition_b_layer_rbsp( )
        public final static int DataPartitionB = 3;
        // Coded slice data partition C slice_data_partition_c_layer_rbsp( )
        public final static int DataPartitionC = 4;
        // Coded slice of an IDR picture slice_layer_without_partitioning_rbsp( )
        public final static int IDR = 5;
        // Supplemental enhancement information (SEI) sei_rbsp( )
        public final static int SEI = 6;
        // Sequence parameter set seq_parameter_set_rbsp( )
        public final static int SPS = 7;
        // Picture parameter set pic_parameter_set_rbsp( )
        public final static int PPS = 8;
        // Access unit delimiter access_unit_delimiter_rbsp( )
        public final static int AccessUnitDelimiter = 9;
        // End of sequence end_of_seq_rbsp( )
        public final static int EOSequence = 10;
        // End of stream end_of_stream_rbsp( )
        public final static int EOStream = 11;
        // Filler data filler_data_rbsp( )
        public final static int FilterData = 12;
        // Sequence parameter set extension seq_parameter_set_extension_rbsp( )
        public final static int SPSExt = 13;
        // Prefix NAL unit prefix_nal_unit_rbsp( )
        public final static int PrefixNALU = 14;
        // Subset sequence parameter set subset_seq_parameter_set_rbsp( )
        public final static int SubsetSPS = 15;
        // Coded slice of an auxiliary coded picture without partitioning slice_layer_without_partitioning_rbsp( )
        public final static int LayerWithoutPartition = 19;
        // Coded slice extension slice_layer_extension_rbsp( )
        public final static int CodedSliceExt = 20;
    }
}
