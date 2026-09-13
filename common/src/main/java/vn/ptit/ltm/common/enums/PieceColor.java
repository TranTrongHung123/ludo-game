package vn.ptit.ltm.common.enums;

public enum PieceColor {
    RED(0, 0),
    BLUE(1, 12),
    YELLOW(2, 24),
    GREEN(3, 36);

    private final int slotIndex;
    private final int startIndex;

    PieceColor(int slotIndex, int startIndex) {
        this.slotIndex = slotIndex;
        this.startIndex = startIndex;
    }

    public int slotIndex() {
        return slotIndex;
    }

    public int startIndex() {
        return startIndex;
    }

    public static PieceColor fromSlotIndex(int slotIndex) {
        for (PieceColor color : values()) {
            if (color.slotIndex == slotIndex) {
                return color;
            }
        }
        throw new IllegalArgumentException("Invalid slot index: " + slotIndex);
    }
}
