package dev.arex.arexhub;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class ArexhubClient implements ClientModInitializer {
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.arexhub.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, "category.arexhub"));

        // H tuşu (ayarlardan değiştirilebilir)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) {
                if (!(client.currentScreen instanceof ArexhubScreen)) {
                    client.setScreen(new ArexhubScreen(client.currentScreen));
                }
            }
        });

        // Ana menü ve duraklatma menüsüne sağ üst köşeye buton
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
                Screens.getButtons(screen).add(
                        ButtonWidget.builder(Text.literal("Arexhub"),
                                        b -> client.setScreen(new ArexhubScreen(screen)))
                                .dimensions(w - 90, 6, 84, 20)
                                .build());
            }
        });
    }
}
