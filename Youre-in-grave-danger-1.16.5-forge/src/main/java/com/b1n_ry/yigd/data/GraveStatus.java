package com.b1n_ry.yigd.data;

public enum GraveStatus {
    UNCLAIMED,
    CLAIMED,
    DESTROYED;

    public int getTransparentColor() {
        switch (this) {
            case CLAIMED:
                return 0x2600FF00;
            case DESTROYED:
                return 0x26FF0000;
            case UNCLAIMED:
                return 0x26FFFF00;
            default:
                return 0x26FFFF00;
        }
    }
}
