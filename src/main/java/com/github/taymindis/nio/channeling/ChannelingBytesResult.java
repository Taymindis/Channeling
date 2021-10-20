package com.github.taymindis.nio.channeling;

public class ChannelingBytesResult {

    private final byte[][] buffs;
    private int buffIndexStart, buffIndexEnd;
    private int offSetOfFirst, limitOfEnd;
    private int readIdx;
    private int totalBytes = -1;

    public ChannelingBytesResult(byte[][] buffs, int buffIndexStart, int buffIndexEnd, int offSetOfFirst, int limitOfEnd) {
        this.buffs = buffs;
        this.readIdx = this.buffIndexStart = buffIndexStart;
        this.buffIndexEnd = buffIndexEnd;
        this.offSetOfFirst = offSetOfFirst;
        this.limitOfEnd = limitOfEnd;
    }

    // TODO lambda or not ?
    public void flush(ChannelingBytesLoop loop) {
        byte[] byteStream = buffs[buffIndexStart];

        // Only one
        if (buffIndexStart == buffIndexEnd) {
            byteStream = buffs[buffIndexEnd];
            loop.consumer(byteStream, offSetOfFirst, limitOfEnd - offSetOfFirst);
            return;
        }


        if (loop.consumer(byteStream, offSetOfFirst, byteStream.length - offSetOfFirst)) {
            for (int i = buffIndexStart + 1; i < buffIndexEnd - 1; i++) {
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
        if(readIdx <= buffIndexEnd) {
            if (readIdx == buffIndexStart) {
                bytes.setBuff(buffs[buffIndexStart]);
                bytes.setOffset(offSetOfFirst);
                if (readIdx == buffIndexEnd) {
                    bytes.setLength(limitOfEnd - offSetOfFirst);
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

    public void resetRead() {
        readIdx = buffIndexStart;
    }

    public byte[] dupBytes() {
        byte[] result = new byte[getTotalBytes()];
        int numOfWrite = 0, len;
        byte[] byteStream = buffs[buffIndexStart];

        // Only one
        if (buffIndexStart == buffIndexEnd) {
            byteStream = buffs[buffIndexEnd];
            System.arraycopy(byteStream, offSetOfFirst, result, 0, limitOfEnd - offSetOfFirst);
            return result;
        }


        len = byteStream.length - offSetOfFirst;
        System.arraycopy(byteStream, offSetOfFirst, result, 0, len);
        numOfWrite += len;


        for (int i = buffIndexStart + 1; i < buffIndexEnd - 1; i++) {
            byteStream = buffs[i];
            len = byteStream.length;
            System.arraycopy(byteStream, 0, result, numOfWrite, len);
            numOfWrite += len;
        }

        byteStream = buffs[buffIndexEnd];
        System.arraycopy(byteStream, 0, result, numOfWrite, limitOfEnd);
        return result;
    }

    public int getTotalBytes() {
        if (totalBytes == -1) {
            if (buffIndexStart == buffIndexEnd) {
                totalBytes = limitOfEnd - offSetOfFirst;
                return totalBytes;
            } else {
                totalBytes = buffs[buffIndexStart].length - offSetOfFirst;
            }
            for (int i = buffIndexStart + 1; i < buffIndexEnd - 1; i++) {
                totalBytes += buffs[i].length;
            }

            totalBytes += limitOfEnd;
        }
        return totalBytes;
    }

    /**
     flip the other half
     * @param stream
     */
    public void flip(ChannelingBytesStream stream) {
        if(this.buffIndexStart == stream.size()-1) {
            this.limitOfEnd = buffs[buffIndexStart].length - limitOfEnd;
        } else {
            this.limitOfEnd = buffs[stream.size()-1].length;
        }

        this.buffIndexStart = buffIndexEnd;
        this.buffIndexEnd = stream.size();
        this.offSetOfFirst = limitOfEnd;


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


