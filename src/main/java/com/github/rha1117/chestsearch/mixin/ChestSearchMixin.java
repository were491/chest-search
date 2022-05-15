package com.github.rha1117.chestsearch.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class ChestSearchMixin extends Screen {
    protected ChestSearchMixin(Text title) {
        super(title);
    }

    private final int SEARCH_BOX_WIDTH = 80; // Config option soon?
    private boolean isChestScreen;
    private boolean isEmptyString;
    private static TextFieldWidget itemSearchBox;
    private ButtonWidget resetButton;

    @Inject(at = @At("RETURN"), method = "init()V")
    private void addSearchBox(CallbackInfo info) {
        // SimpleInventory isn't a ChestBlockEntity tho...
        // Checking once should be fine since it creates a new object for every time we make a HandledScreen anyways
        isChestScreen = ((ChestSearchAccessor) this).getHandler() instanceof GenericContainerScreenHandler handler &&
                handler.getInventory() instanceof SimpleInventory;
        if (!isChestScreen) return;

        itemSearchBox = new TextFieldWidget(MinecraftClient.getInstance().textRenderer,
                4, this.height - 22, SEARCH_BOX_WIDTH, 16, itemSearchBox, new LiteralText("Type to search..."));
        this.addSelectableChild(itemSearchBox);
        itemSearchBox.setChangedListener(str -> isEmptyString = str.trim().equals(""));
        isEmptyString = itemSearchBox.getText().trim().equals("");

        resetButton = new ButtonWidget(SEARCH_BOX_WIDTH + 8, this.height - 24, 36, 20,
                new LiteralText("Reset"), button -> itemSearchBox.setText(""));
        this.addSelectableChild(resetButton);
    }

    @Inject(at = @At("RETURN"), method = "tick()V")
    private void tickSearchBox(CallbackInfo info) {
        if (!isChestScreen) return;
        itemSearchBox.tick();
    }

    @Inject(at = @At("HEAD"), method = "keyPressed(III)Z", cancellable = true)
    private void checkKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> info) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            info.setReturnValue(true);
        } else if (isChestScreen && itemSearchBox.isActive()) {
            itemSearchBox.keyPressed(keyCode, scanCode, modifiers);
            info.setReturnValue(true);
        }
    }

    @Inject(at = @At("TAIL"), method = "drawSlot(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/screen/slot/Slot;)V")
    private void renderMatchingResults(MatrixStack matrices, Slot slot, CallbackInfo info) {
        if (!isChestScreen || isEmptyString) return;

        // TODO: use namespaces
        int color;
        if (slot.getStack().getName().getString().toLowerCase()
                .contains(itemSearchBox.getText().trim().toLowerCase())) {
            // half the brightness of the vanilla highlight
            color = 1090519039;
        } else {
            color = -2147483648;
        }

        // TODO make code go zoom
        // copied from HandledScreen.class -> drawSlotHighlight
        RenderSystem.disableDepthTest();
        RenderSystem.colorMask(true, true, true, false);
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        fillGradient(matrices.peek().getPositionMatrix(), bufferBuilder,
                slot.x, slot.y, slot.x + 16, slot.y + 16,
                this.getZOffset(), color, color);
        tessellator.draw();
        RenderSystem.disableBlend();
        RenderSystem.enableTexture();
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableDepthTest();
    }

    @Inject(at = @At("RETURN"), method = "render(Lnet/minecraft/client/util/math/MatrixStack;IIF)V")
    private void renderSearchBox(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo info) {
        if (!isChestScreen) return;
        itemSearchBox.render(matrices, mouseX, mouseY, delta);
        resetButton.render(matrices, mouseX, mouseY, delta);

        // color -> packed ARGB representation where each value is 8 bits
        // See TextRenderer.class, first 8 bits are A, last 8 bits are B.
        // -1 = 11111111-11111111-11111111-11111111 or: white.
        MinecraftClient.getInstance().textRenderer.draw(matrices, "Search:", 4, this.height - 34, -1);
    }
}
