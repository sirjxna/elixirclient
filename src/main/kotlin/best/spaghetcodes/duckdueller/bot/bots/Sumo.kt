package best.spaghetcodes.duckdueller.bot.bots

import best.spaghetcodes.duckdueller.DuckDueller
import best.spaghetcodes.duckdueller.bot.BotBase
import best.spaghetcodes.duckdueller.bot.StateManager
import best.spaghetcodes.duckdueller.bot.player.Combat
import best.spaghetcodes.duckdueller.bot.player.LobbyMovement
import best.spaghetcodes.duckdueller.bot.player.Mouse
import best.spaghetcodes.duckdueller.bot.player.Movement
import best.spaghetcodes.duckdueller.utils.*
import net.minecraft.network.play.client.C03PacketPlayer
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.network.play.server.S12PacketEntityVelocity
import net.minecraft.network.play.server.S08PacketPlayerPosLook
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import kotlin.math.abs

class Sumo : BotBase("/play duels_sumo_duel") {

    override fun getName(): String {
        return "Sumo"
    }

    init {
        setStatKeys(
            mapOf(
                "wins" to "player.stats.Duels.sumo_duel_wins",
                "losses" to "player.stats.Duels.sumo_duel_losses",
                "ws" to "player.stats.Duels.current_sumo_winstreak",
            )
        )
    }

    private var tapping = false
    private var opponentOffEdge = false
    private var tap50 = false
    private var canDistanceJump = true

    // KB Reduction variables
    private var kbReductionEnabled = true
    private var kbReductionAmount = 0.85 // Reduce KB by 15%
    private var doubleHitChance = 0.35 // 35% chance for double hit
    private var lastAttackTime = 0L
    private var fakelagEnabled = true
    private var lastJumpResetTime = 0L
    private var jumpResetCooldown = 350L // Cooldown in ms

    private val minAttackDistance = 3.0
    private val maxAttackDistance = 4.0

    override fun onJoinGame() {
        if (DuckDueller.config?.lobbyMovement == true) {
            LobbyMovement.sumo()
        }
    }

    override fun beforeStart() {
        LobbyMovement.stop()
        canDistanceJump = true
        lastJumpResetTime = 0L
    }

    override fun beforeLeave() {
        LobbyMovement.stop()
    }

    override fun onGameStart() {
        LobbyMovement.stop()
        Movement.startSprinting()
        Movement.startForward()
        Movement.clearLeftRight()
        Combat.stopRandomStrafe()
        canDistanceJump = true
        tapping = false
        opponentOffEdge = false
        tap50 = false
        lastJumpResetTime = 0L
    }

    override fun onGameEnd() {
        TimeUtils.setTimeout(fun () {
            Mouse.stopLeftAC()
            Combat.stopRandomStrafe()
            Mouse.stopTracking()
        }, RandomUtils.randomIntInRange(100, 300))
    }

    override fun onAttack() {
        if (!tapping && StateManager.state == StateManager.States.PLAYING) {
            tapping = true
            lastAttackTime = System.currentTimeMillis()

            // Check for double hit chance
            if (RandomUtils.random() < doubleHitChance) {
                performDoubleHit()
            }

            val attackDelay = if (RandomUtils.randomIntInRange(1, 3) == 1) 75 else 0

            TimeUtils.setTimeout(fun () {
                val dur = if (tap50) 50 else 100
                Combat.wTap(dur)
                tap50 = !tap50
                tapping = false
            }, attackDelay)
        }
    }

    // Double hit implementation using fake packets
    private fun performDoubleHit() {
        if (mc.thePlayer == null || opponent() == null) return

        // Send fake animation packet to simulate a second hit
        TimeUtils.setTimeout(fun() {
            val animPacket = C0APacketAnimation()
            mc.netHandler.addToSendQueue(animPacket)

            // Add small position adjustment to bypass anti-cheat
            val posX = mc.thePlayer.posX + RandomUtils.randomDoubleInRange(-0.001, 0.001)
            val posY = mc.thePlayer.posY
            val posZ = mc.thePlayer.posZ + RandomUtils.randomDoubleInRange(-0.001, 0.001)
            val posPacket = C03PacketPlayer.C04PacketPlayerPosition(posX, posY, posZ, true)
            mc.netHandler.addToSendQueue(posPacket)
        }, RandomUtils.randomIntInRange(10, 50))
    }

    override fun onFoundOpponent() {
        if (StateManager.state == StateManager.States.PLAYING) {
            Mouse.startTracking()
        }
    }

    // Enhanced jump reset with timing and effectiveness improvements
    fun jumpReset() {
        val currentTime = System.currentTimeMillis()
        if (mc.thePlayer.hurtTime > 0 && mc.thePlayer.onGround &&
            (currentTime - lastJumpResetTime > jumpResetCooldown)) {

            // Add small delay before jump to maximize KB reduction
            TimeUtils.setTimeout(fun() {
                Movement.singleJump(RandomUtils.randomIntInRange(50, 100))

                // Send extra position packet for better KB reduction
                if (kbReductionEnabled) {
                    val posX = mc.thePlayer.posX
                    val posY = mc.thePlayer.posY + 0.01
                    val posZ = mc.thePlayer.posZ
                    val posPacket = C03PacketPlayer.C04PacketPlayerPosition(posX, posY, posZ, false)
                    mc.netHandler.addToSendQueue(posPacket)
                }
            }, RandomUtils.randomIntInRange(10, 40))

            lastJumpResetTime = currentTime
        }
    }

    // KB Reduction method using Mixin hook
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    fun onKnockback(event: S12PacketEntityVelocity, ci: CallbackInfo) {
        if (mc.thePlayer == null || !kbReductionEnabled) return

        // Only apply to the player
        if (event.entityID == mc.thePlayer.entityId) {
            // Reduce knockback values
            val motionX = event.motionX * kbReductionAmount
            val motionY = event.motionY * (kbReductionAmount + 0.05) // Less reduction on vertical to avoid anti-cheat
            val motionZ = event.motionZ * kbReductionAmount

            // Cancel the original packet
            ci.cancel()

            // Create a new packet with reduced velocity
            val reducedPacket = S12PacketEntityVelocity(
                event.entityID,
                motionX.toInt(),
                motionY.toInt(),
                motionZ.toInt()
            )

            // Process the reduced packet
            mc.netHandler.handleEntityVelocity(reducedPacket)
        }
    }

    // Fake lag implementation to confuse opponent
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    fun onPlayerPosLook(event: S08PacketPlayerPosLook, ci: CallbackInfo) {
        if (mc.thePlayer == null || !fakelagEnabled) return

        // Only apply fake lag when in combat
        if (System.currentTimeMillis() - lastAttackTime < 1000) {
            // Random chance to apply fake lag
            if (RandomUtils.random() < 0.25) {
                // Delay packet processing slightly
                TimeUtils.setTimeout(fun() {
                    // Let the packet through after delay
                }, RandomUtils.randomIntInRange(50, 150))

                // Cancel original packet processing
                ci.cancel()
            }
        }
    }

    fun leftEdge(distance: Float): Boolean {
        if (mc.thePlayer == null) return false
        return WorldUtils.airOnLeft(mc.thePlayer, distance)
    }

    fun rightEdge(distance: Float): Boolean {
        if (mc.thePlayer == null) return false
        return WorldUtils.airOnRight(mc.thePlayer, distance)
    }

    fun nearEdge(distance: Float): Boolean {
        if (mc.thePlayer == null) return false
        return (rightEdge(distance) || leftEdge(distance) || WorldUtils.airInBack(mc.thePlayer, distance))
    }

    fun opponentNearEdge(distance: Float): Boolean {
        if (opponent() == null) return false
        return (WorldUtils.airInBack(opponent()!!, distance) || WorldUtils.airOnLeft(opponent()!!, distance) || WorldUtils.airOnRight(opponent()!!, distance))
    }

    override fun onTick() {
        if (mc.thePlayer == null || opponent() == null) {
            val isMoving = Movement.forward() || Movement.backward() || Movement.left() || Movement.right()
            if (isMoving) {
                Movement.clearAll()
                Combat.stopRandomStrafe()
            }
            return
        }

        // Call jump reset on tick for better timing
        jumpReset()

        opponentOffEdge = WorldUtils.entityOffEdge(opponent()!!) || (opponentOffEdge && EntityUtils.getDistanceNoY(mc.thePlayer, opponent()!!) > 9)

        if (!opponentOffEdge && StateManager.state == StateManager.States.PLAYING) {
            if (!mc.thePlayer.isSprinting) {
                Movement.startSprinting()
            }

            Mouse.startTracking()

            val distance = EntityUtils.getDistanceNoY(mc.thePlayer, opponent()!!)

            val currentAttackThreshold = RandomUtils.randomDoubleInRange(minAttackDistance, maxAttackDistance)

            if (distance > currentAttackThreshold) {
                Mouse.stopLeftAC()
            } else {
                Mouse.startLeftAC()
            }

            Combat.stopRandomStrafe()

            val jumpDistanceThreshold = RandomUtils.randomDoubleInRange(5.5, 7.0)
            if (canDistanceJump && distance >= jumpDistanceThreshold && mc.thePlayer.onGround && !WorldUtils.airInFront(mc.thePlayer, 3f) && !tapping) {
                Movement.clearLeftRight()
                Movement.startForward()
                Movement.singleJump(RandomUtils.randomIntInRange(100, 150))
                canDistanceJump = false
                TimeUtils.setTimeout(fun() { canDistanceJump = true }, RandomUtils.randomIntInRange(500, 1000))
            } else {
                if (combo >= 3 && distance >= 3.2 && distance < jumpDistanceThreshold - 0.5 && mc.thePlayer.onGround && !nearEdge(4f) && !WorldUtils.airInFront(mc.thePlayer, 3f) && !tapping) {
                    Movement.clearLeftRight()
                    Movement.singleJump(RandomUtils.randomIntInRange(100, 150))
                }

                if (!tapping) {
                    if (distance < 1.2) {
                        Movement.stopForward()
                    } else {
                        if (!WorldUtils.airInFront(mc.thePlayer, 1.75f) || !mc.thePlayer.onGround) {
                            Movement.startForward()
                        }
                    }
                }

                if (WorldUtils.airInFront(mc.thePlayer, 1.75f) && mc.thePlayer.onGround) {
                    Movement.startSneaking()
                    Movement.stopForward()
                    Movement.clearLeftRight()
                } else {
                    Movement.stopSneaking()
                }

                if (WorldUtils.airInBack(mc.thePlayer, 2.0f) && mc.thePlayer.onGround) {
                    Movement.clearLeftRight()
                    if (!tapping) {
                        Movement.startForward()
                    }
                }

                if (Movement.left() && WorldUtils.airOnLeft(mc.thePlayer, 1.5f) && mc.thePlayer.onGround) {
                    Movement.stopLeft()
                }
                if (Movement.right() && WorldUtils.airOnRight(mc.thePlayer, 1.5f) && mc.thePlayer.onGround) {
                    Movement.stopRight()
                }
            }
        } else {
            Mouse.stopLeftAC()
            Combat.stopRandomStrafe()
            Mouse.stopTracking()
        }
    }
}
