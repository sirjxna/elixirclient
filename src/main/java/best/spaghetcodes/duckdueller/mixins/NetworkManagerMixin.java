package best.spaghetcodes.duckdueller.mixins

import best.spaghetcodes.duckdueller.DuckDueller
import net.minecraft.client.Minecraft
import net.minecraft.network.NetworkManager
import net.minecraft.network.Packet
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

/**
 * Mixin to inject into the animation packet handling for double hits
 */
@Mixin(NetworkManager::class)
class NetworkManagerMixin {

    @Inject(method = ["sendPacket(Lnet/minecraft/network/Packet;)V"], at = [At("HEAD")])
    private fun onSendPacket(packet: Packet<*>, ci: CallbackInfo) {
        // Check if this is a swing animation packet (C0APacketAnimation)
        if (packet is net.minecraft.network.play.client.C0APacketAnimation) {
            val mc = Minecraft.getMinecraft()
            val currentBot = DuckDueller.instance.botHandler.currentBot

            // If we're using the Sumo bot, notify it about the animation
            // This allows the bot to send a second hit if needed
            if (currentBot != null && currentBot.getName() == "Sumo" && mc.thePlayer != null) {
                if (currentBot is best.spaghetcodes.duckdueller.bot.bots.Sumo) {
                    // We don't need to do anything here since onAttack() already
                    // handles the double hit chance, but we could add additional logic
                }
            }
        }
    }
}
