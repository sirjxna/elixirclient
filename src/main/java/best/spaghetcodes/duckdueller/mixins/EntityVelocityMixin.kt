package best.spaghetcodes.duckdueller.mixins

import best.spaghetcodes.duckdueller.DuckDueller
import net.minecraft.client.Minecraft
import net.minecraft.client.network.NetHandlerPlayClient
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Mixin to modify incoming knockback packets for KB reduction
 */
@Mixin(NetHandlerPlayClient::class)
class EntityVelocityMixin {

    @Inject(method = ["handleEntityVelocity"], at = [At("HEAD")], cancellable = true)
    private fun onEntityVelocity(packet: S12PacketEntityVelocity, ci: CallbackInfo) {
        val mc = Minecraft.getMinecraft()

        // Only modify velocity if the packet is for the player and we're in a game
        if (mc.thePlayer != null && mc.thePlayer.entityId == packet.entityID) {
            val currentBot = DuckDueller.instance.botHandler.currentBot

            // Check if we're using the Sumo bot with KB reduction enabled
            if (currentBot != null && currentBot.getName() == "Sumo") {
                // Let the Sumo class handle KB reduction
                // We pass the packet and callback info to the bot for processing
                if (currentBot is best.spaghetcodes.duckdueller.bot.bots.Sumo) {
                    currentBot.onKnockback(packet, ci)
                }
            }
        }
    }

    @Inject(method = ["handlePlayerPosLook"], at = [At("HEAD")], cancellable = true)
    private fun onPlayerPosLook(packet: S08PacketPlayerPosLook, ci: CallbackInfo) {
        val mc = Minecraft.getMinecraft()

        if (mc.thePlayer != null) {
            val currentBot = DuckDueller.instance.botHandler.currentBot

            // Check if we're using the Sumo bot with fake lag enabled
            if (currentBot != null && currentBot.getName() == "Sumo") {
                // Let the Sumo class handle fake lag position packets
                if (currentBot is best.spaghetcodes.duckdueller.bot.bots.Sumo) {
                    currentBot.onPlayerPosLook(packet, ci)
                }
            }
        }
    }
}
