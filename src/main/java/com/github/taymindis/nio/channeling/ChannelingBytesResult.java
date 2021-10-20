package com.github.taymindis.nio.channeling;

public class ChannelingBytesResult {

    private final byte[][] buffs;
    private final int buffIndexStart, buffIndexEnd;
    private final int offSetOfFirst, limitOfEnd;
    private int totalBytes = -1;

    public ChannelingBytesResult(byte[][] buffs, int buffIndexStart, int buffIndexEnd, int offSetOfFirst, int limitOfEnd) {
        this.buffs = buffs;
        this.buffIndexStart = buffIndexStart;
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


