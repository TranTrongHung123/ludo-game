package vn.ptit.ltm.server.game;

import vn.ptit.ltm.common.model.GameConstants;

import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface DiceRoller {
    int roll();

    // Tạo nguồn xúc xắc phía Server với giá trị từ 1 đến 6.
    static DiceRoller random() {
        return () -> ThreadLocalRandom.current().nextInt(
                GameConstants.DICE_MIN,
                GameConstants.DICE_MAX + 1
        );
    }
}
