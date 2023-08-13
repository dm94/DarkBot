package com.github.manolo8.darkbot.config.actions.values;

import java.util.Locale;
import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.actions.Value;
import com.github.manolo8.darkbot.config.actions.ValueData;
import com.github.manolo8.darkbot.core.manager.StatsManager;

@ValueData(name = "stat-type", description = "Gets a certain Stat type from a bot", example = "stat-type(hp-percent, a)")
public class StatTypeValue implements Value<Number> {
    public StatsManager statsManager;
    public StatType statType;

    public StatTypeValue() {
    }

    @Override
    public @Nullable Number get(Main main) {
        statsManager = main.statsManager;
        return statType != null ? statType.getter.apply(statsManager) : null;
    }

    public enum StatType {
        TOTAL_CREDITS(StatsManager::getTotalCredits),
        EARNED_CREDITS(StatsManager::getEarnedCredits),
        TOTAL_URIDIUM(StatsManager::getTotalUridium),
        EARNED_UDIDIUM(StatsManager::getEarnedUridium),
        TOTAL_EXPERIENCIE(StatsManager::getTotalExperience),
        EARNED_EXPERIENCIE(StatsManager::getEarnedExperience),
        TOTAL_HONOR(StatsManager::getTotalHonor),
        EARNED_HONOR(StatsManager::getEarnedHonor),
        CARGO(StatsManager::getCargo),
        MAX_CARGO(StatsManager::getMaxCargo);

        private final Function<StatsManager, Number> getter;

        StatType(Function<StatsManager, Number> getter) {
            this.getter = getter;
        }

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT).replace("_", "-");
        }

        public static StatType of(String statType) {
            for (StatType ht : StatType.values()) {
                if (ht.toString().equals(statType))
                    return ht;
            }
            return null;
        }
    }

    @Override
    public String toString() {
        return "stat-type(" + statType + ")";
    }

}
