package com.fibermc.essentialcommands.commands;

import com.fibermc.essentialcommands.ECText;
import com.fibermc.essentialcommands.PlayerData;
import com.fibermc.essentialcommands.PlayerTeleporter;
import com.fibermc.essentialcommands.QueuedPlayerTeleport;
import com.fibermc.essentialcommands.access.ServerPlayerEntityAccess;
import com.fibermc.essentialcommands.config.Config;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;

public class TeleportAcceptCommand implements Command<ServerCommandSource> {

    public TeleportAcceptCommand() {}
    
    @Override
    public int run(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        //Store command sender
        ServerPlayerEntity senderPlayer = source.getPlayer();
        //Store Target Player
        ServerPlayerEntity targetPlayer = EntityArgumentType.getPlayer(context, "target");
        PlayerData targetPlayerData = ((ServerPlayerEntityAccess)targetPlayer).getEcPlayerData();

        //identify if target player did indeed request to teleport. Continue if so, otherwise throw exception.
        if (targetPlayerData.getTpTarget() != null && targetPlayerData.getTpTarget().getPlayer().equals(senderPlayer)) {

            //inform target player that teleport has been accepted via chat
            targetPlayer.sendSystemMessage(
                ECText.getInstance().getText("cmd.tpaccept.feedback").setStyle(Config.FORMATTING_DEFAULT)
                , Util.NIL_UUID);

            //Conduct teleportation
            PlayerTeleporter.requestTeleport(new QueuedPlayerTeleport(
                targetPlayerData,
                senderPlayer,
                senderPlayer.getDisplayName()
            ));

            //Clean up TPAsk
            targetPlayerData.setTpTimer(-1);

            //Send message to command sender confirming that request has been accepted
            source.sendFeedback(
                ECText.getInstance().getText("cmd.tpaccept.feedback").setStyle(Config.FORMATTING_DEFAULT)
                , Config.BROADCAST_TO_OPS);
            return 1;
        } else {
            source.sendError(
                ECText.getInstance().getText("cmd.tpa_reply.error.no_request_from_target").setStyle(Config.FORMATTING_ERROR)
            );
            return -1;
        }
    }

}
