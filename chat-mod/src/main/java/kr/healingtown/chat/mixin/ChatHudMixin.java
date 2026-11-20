package kr.healingtown.chat.mixin;

import kr.healingtown.chat.HtChat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 바닐라 채팅 (왼쪽 아래) 은 안 그리고, 들어오는 메시지만 받아 감
 *  바닐라 채팅 기록 (위 · 아래 화살표로 지난 입력) 은 그대로
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void htchat$render(DrawContext context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (HtChat.active()) ci.cancel();
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
    private void htchat$addMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        HtChat.onMessage(message);
    }

    /** 안 보이는 바닐라 채팅 줄을 눌러 링크가 열리지 않게 */
    @Inject(method = "getTextStyleAt", at = @At("HEAD"), cancellable = true)
    private void htchat$getTextStyleAt(double x, double y, CallbackInfoReturnable<Style> cir) {
        if (HtChat.active()) cir.setReturnValue(null);
    }

    @Inject(method = "getIndicatorAt", at = @At("HEAD"), cancellable = true)
    private void htchat$getIndicatorAt(double mouseX, double mouseY, CallbackInfoReturnable<MessageIndicator> cir) {
        if (HtChat.active()) cir.setReturnValue(null);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void htchat$mouseClicked(double mouseX, double mouseY, CallbackInfoReturnable<Boolean> cir) {
        if (HtChat.active()) cir.setReturnValue(false);
    }

    /** F3+D (채팅 지우기) · 서버를 나갈 때 */
    @Inject(method = "clear", at = @At("HEAD"))
    private void htchat$clear(boolean clearHistory, CallbackInfo ci) {
        HtChat.store.clear();
    }
}
