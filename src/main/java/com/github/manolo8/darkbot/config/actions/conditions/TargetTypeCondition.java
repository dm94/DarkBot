package com.github.manolo8.darkbot.config.actions.conditions;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;

import com.github.manolo8.darkbot.Main;
import com.github.manolo8.darkbot.config.actions.Condition;
import com.github.manolo8.darkbot.config.actions.Parser;
import com.github.manolo8.darkbot.config.actions.SyntaxException;
import com.github.manolo8.darkbot.config.actions.Value;
import com.github.manolo8.darkbot.config.actions.ValueData;
import com.github.manolo8.darkbot.config.actions.parser.ParseResult;
import com.github.manolo8.darkbot.config.actions.parser.ParseUtil;
import com.github.manolo8.darkbot.config.actions.parser.ValueParser;
import com.github.manolo8.darkbot.core.entities.Ship;

import eu.darkbot.api.game.entities.Npc;
import eu.darkbot.api.game.other.EntityInfo.Diplomacy;

@ValueData(name = "is-relationship", description = "Checks the target type", example = "is-relationship(npc, target())")
public class TargetTypeCondition implements Condition, Parser {
    public TargetType type;
    public Value<Ship> ship;

    @Override
    public @NotNull Condition.Result get(Main main) {
        Ship target;

        if (type == null) {
            return Result.ABSTAIN;
        }

        target = Value.get(ship, main);
        if (target == null || !target.isValid()) {
            return type == TargetType.NO_TARGET ? Result.ALLOW : Result.DENY;
        }

        if (type == TargetType.NPC && target instanceof Npc) {
            return Result.ALLOW;
        }

        if (type == TargetType.ENEMY && target.getEntityInfo().isEnemy()) {
            return Result.ALLOW;
        }

        Diplomacy diplomacy = target.getEntityInfo().getClanDiplomacy();
        if (diplomacy == Diplomacy.ALLIED) {
            return type == TargetType.ALLIED ? Result.ALLOW : Result.DENY;
        } else if (diplomacy == Diplomacy.NOT_ATTACK_PACT) {
            return type == TargetType.NOT_ATTACK_PACT ? Result.ALLOW : Result.DENY;
        }

        return Result.ABSTAIN;
    }

    public enum TargetType {
        NO_TARGET,
        NPC,
        ENEMY,
        ALLIED,
        NOT_ATTACK_PACT;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT).replace("_", "-");
        }

        public static TargetType of(String targetType) {
            for (TargetType tT : TargetType.values()) {
                if (tT.toString().equals(targetType))
                    return tT;
            }
            return null;
        }
    }

    @Override
    public String toString() {
        return "is-relationship(" + type + ", " + ship + ")";
    }

    @Override
    public String parse(String str) throws SyntaxException {
        String[] params = str.split(" *, *", 2);

        type = TargetType.of(params[0].trim());

        if (type == null) {
            throw new SyntaxException("Unknown is-relationship: '" + params[0] + "'", str, TargetType.class);
        }

        str = ParseUtil.separate(params, getClass(), ",");

        ParseResult<Ship> pr = ValueParser.parse(str, Ship.class);
        ship = pr.value;

        return ParseUtil.separate(pr.leftover.trim(), getClass(), ")");
    }
}