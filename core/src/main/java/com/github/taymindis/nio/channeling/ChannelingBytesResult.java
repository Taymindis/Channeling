package com.github.taymindis.nio.channeling;

public class ChannelingBytesResult {

    private final byte[][] buffs;
    private final ChannelingBytesStream stream;
    private int buffIndexStart, buffIndexEnd;
    private int offSetOfFirst, limitOfEnd;
    private int readIdx;
    private int totalBytes = -1;

    public ChannelingBytesResult(ChannelingBytesStream stream, byte[][] buffs, int buffIndexStart, int buffIndexEnd, int offSetOfFirst, int limitOfEnd) {
        this.stream = stream;
        this.buffs = buffs;
        this.readIdx = this.buffIndexStart = buffIndexStart;
        this.buffIndexEnd = buffIndexEnd;
        this.offSetOfFirst = offSetOfFirst;
        this.limitOfEnd = limitOfEnd;
    }

    public void forEach(ChannelingBytesLoop loop) {
        if (buffIndexStart > buffIndexEnd) {
            return;
        }

        byte[] byteStream = buffs[buffIndexStart];

        // Only one
        if (buffIndexStart == buffIndexEnd) {
            byteStream = buffs[buffIndexEnd];
            loop.consumer(byteStream, offSetOfFirst, limitOfEnd - offSetOfFirst);
            return;
        }

        if (loop.consumer(byteStream, offSetOfFirst, byteStream.length - offSetOfFirst)) {
            for (int i = buffIndexStart + 1; i < buffIndexEnd; i++) {
                byteStream = buffs[i];
                if (!loop.consumer(byteStream, 0, byteStream.length)) {
                    return;
                }
            }

            byteStream = buffs[buffIndexEnd];
            loop.consumer(byteStream, 0, limitOfEnd);
        }
    }

    public boolean read(ChannelingBytes bytes) {
        if (readIdx <= buffIndexEnd) {
            if (readIdx == buffIndexStart) {
                byte[] buff = buffs[buffIndexStart];
                bytes.setBuff(buff);
                bytes.setOffset(offSetOfFirst);
                if (readIdx == buffIndexEnd) {
                    bytes.setLength(limitOfEnd - offSetOfFirst);
                } else {
                    bytes.setLength(buff.length - offSetOfFirst);
                }
            } else {
                byte[] buff = buffs[readIdx];
                bytes.setBuff(buff);
                bytes.setOffset(0);
                if (readIdx == buffIndexEnd) {
                    bytes.setLength(limitOfEnd);
                } else {
                    bytes.setLength(buff.length);
                }
            }
            readIdx++;
            return true;
        }
        return false;
    }


    public boolean readUntilLast(ChannelingBytes bytes) {
        if (read(bytes)) {
            return readIdx <= buffIndexEnd;
        }
        bytes.setBuff(null);
        bytes.setOffset(0);
        bytes.setLength(0);
        return false;
    }

    public void resetRead() {
        readIdx = buffIndexStart;
    }

    public byte[] dupBytes() {
        byte[] result = new byte[getTotalBytes()];
        final int[] totalWrite = {0};
        forEach(new ChannelingBytesLoop() {
            @Override
            public boolean consumer(byte[] bytes, int offset, int length) {
                System.arraycopy(bytes, offset, result, totalWrite[0], length);
                totalWrite[0] += length;
                return true;
            }
        });
        return result;
    }

    public int getTotalBytes() {
        if (totalBytes == -1) {
            if (buffIndexStart > buffIndexEnd) {
                totalBytes = 0;
                return totalBytes;
            }
            if (buffIndexStart == buffIndexEnd) {
                totalBytes = limitOfEnd - offSetOfFirst;
                return totalBytes;
            } else {
                totalBytes = buffs[buffIndexStart].length - offSetOfFirst;
            }
            for (int i = buffIndexStart + 1; i < buffIndexEnd; i++) {
                totalBytes += buffs[i].length;
            }

            totalBytes += limitOfEnd;
        }
        return totalBytes;
    }

    /**
     * Flip the bytes to forward
     *
     * @return flipped Result
     */
    public ChannelingBytesResult flipForward() {
        int flipBuffIndexStart = this.buffIndexEnd,
                flipBuffIndexEnd = stream.getTotalBuffers() - 1,
                flipOffSetOfFirst = this.limitOfEnd,
                flipLimitOfEnd = buffs[flipBuffIndexEnd].length;
        return new ChannelingBytesResult(stream, buffs, flipBuffIndexStart, flipBuffIndexEnd, flipOffSetOfFirst, flipLimitOfEnd);
    }

    /**
     * Flip the bytes to backward
     *
     * @return flipped Result
     */
    public ChannelingBytesResult flipBackward() {
        int flipBuffIndexStart = 0,
                flipBuffIndexEnd = this.buffIndexStart,
                flipOffSetOfFirst = 0,
                flipLimitOfEnd = offSetOfFirst;
        return new ChannelingBytesResult(stream, buffs, flipBuffIndexStart, flipBuffIndexEnd, flipOffSetOfFirst, flipLimitOfEnd);
    }

    public int getBuffIndexStart() {
        return buffIndexStart;
    }

    public int getBuffIndexEnd() {
        return buffIndexEnd;
    }

    public int getOffSetOfFirst() {
        return offSetOfFirst;
    }

    public int getLimitOfEnd() {
        return limitOfEnd;
    }
}


