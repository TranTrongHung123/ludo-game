package vn.ptit.ltm.server.game;

import vn.ptit.ltm.common.model.GameConstants;

import java.util.concurrent.ThreadLocalRandom;

@FunctionalInterface
public interface DiceRoller {
    int roll();

    static DiceRoller random() {
        return () -> ThreadLocalRandom.current().nextInt(
                GameConstants.DICE_MIN,
                GameConstants.DICE_MAX + 1
        );
    }
}
