package com.eventopvp;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;

final class InviteDialog {
    private InviteDialog() {}

    static void show(EventoPvP plugin, EventManager manager, Messages msg, Player p) {
        try {
            send(plugin, manager, msg, p);
        } catch (RuntimeException ex) {
            // si el cliente no soporta diálogos, queda el mensaje clicable y /evento aceptar
            plugin.getLogger().fine("No se pudo mostrar el diálogo a " + p.getName() + ": " + ex.getMessage());
        }
    }

    private static void send(EventoPvP plugin, EventManager manager, Messages msg, Player p) {
        ActionButton yes = ActionButton.create(
                msg.raw("dialog-yes"),
                msg.raw("dialog-yes-tip"),
                130,
                DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player pl) {
                        Bukkit.getScheduler().runTask(plugin, () -> manager.join(pl));
                    }
                }, ClickCallback.Options.builder().uses(1).build()));
        ActionButton no = ActionButton.create(msg.raw("dialog-no"), msg.raw("dialog-no-tip"), 130, null);
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(msg.raw("dialog-title"))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(msg.raw("dialog-body"))))
                        .build())
                .type(DialogType.confirmation(yes, no)));
        p.showDialog(dialog);
    }
}
