package carpet.commands;

import carpet.CarpetSettings;
import carpet.helpers.HopperCounter;
import carpet.utils.Messenger;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.text.ITextComponent;

public class CounterCommand
{
    public static void register(CommandDispatcher<CommandSource> dispatcher)
    {
        LiteralArgumentBuilder<CommandSource> literalargumentbuilder = Commands.literal("counter").executes((context)
         -> listAllCounters(context.getSource(), false)).requires((player) ->
                CarpetSettings.getBool("hopperCounters"));

        literalargumentbuilder.
                then((Commands.literal("reset").executes( (p_198489_1_)-> resetCounter(p_198489_1_.getSource(), null))));
        for (EnumDyeColor enumDyeColorolor: EnumDyeColor.values())
        {
            String color = enumDyeColorolor.toString();
            literalargumentbuilder.
                    then((Commands.literal(color).executes( (p_198489_1_)-> displayCounter(p_198489_1_.getSource(), color, false))));
            literalargumentbuilder.then(Commands.literal(color).
                    then(Commands.literal("reset").executes((context) ->
                            resetCounter(context.getSource(), color))));
            literalargumentbuilder.then(Commands.literal(color).
                    then(Commands.literal("realtime").executes((context) ->
                            displayCounter(context.getSource(), color, true))));
        }
        dispatcher.register(literalargumentbuilder);
    }

    private static int displayCounter(CommandSource source, String color, boolean realtime)
    {
        for (ITextComponent message: HopperCounter.query_hopper_stats_for_color(source.getServer(), color, realtime, false))
        {
            source.sendFeedback(message, false);
        }
        return 1;
    }

    private static int resetCounter(CommandSource source, String color)
    {
        HopperCounter.reset_hopper_counter(source.getWorld(), color);
        if (color == null)
        {
            source.sendFeedback(Messenger.m(null, "w Restarted all counters"), false);

        }
        else
        {
            source.sendFeedback(Messenger.m(null, "w Restarted "+color+" counter"), false);

        }
        return 1;
    }

    private static int listAllCounters(CommandSource source, boolean realtime)
    {
        for (ITextComponent message: HopperCounter.query_hopper_all_stats(source.getServer(), realtime))
        {
            source.sendFeedback(message, false);
        }
        return 1;
    }

}