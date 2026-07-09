package net.thewinnt.cutscenes.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.thewinnt.cutscenes.CutsceneAPI;
import net.thewinnt.cutscenes.event.CutsceneEvents;
import net.thewinnt.cutscenes.neoforge.event.CutsceneOverEvent;
import net.thewinnt.cutscenes.platform.PacketType;

@Mod("cutscene_api")
public final class CutsceneAPINeoForge {
    public static final NeoForgePlatform PLATFORM = new NeoForgePlatform();

    public CutsceneAPINeoForge(IEventBus bus, Dist dist) {
        // Run our common setup.
        CutsceneAPI.onInitialize(PLATFORM);
        CutsceneAPIEntities.REGISTRY.register(bus);
        CutsceneAPIArgumentTypes.REGISTRY.register(bus);
        bus.addListener(CutsceneAPINeoForge::registerPayloadHandlers);
        if (dist == Dist.CLIENT) {
            CutsceneAPINeoForgeClient.init();
        }
        CutsceneEvents.CUTSCENE_OVER_SERVER.addListener((type, id, player, reason) -> {
            NeoForge.EVENT_BUS.post(new CutsceneOverEvent.Server(player, type, id, reason));
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        for (PacketType packetType : PLATFORM.clientboundPackets) {
            NeoForgePlatform.registerClientboundPacket(registrar, packetType);
        }

        for (PacketType packetType : PLATFORM.serverboundPackets) {
            NeoForgePlatform.registerServerboundPacket(registrar, packetType);
        }

        PLATFORM.clientboundPackets = null;
        PLATFORM.serverboundPackets = null;
    }
}
