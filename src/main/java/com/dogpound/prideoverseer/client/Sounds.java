package com.dogpound.prideoverseer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundEvent;

/** Little UI sounds: opening the view, selecting, giving orders (units answer in their own voice). */
final class Sounds {
    private Sounds() {}
    static final SoundEvent OPEN = SoundEvents.BLOCK_NOTE_CHIME, CLOSE = SoundEvents.BLOCK_NOTE_BELL, SELECT = SoundEvents.UI_BUTTON_CLICK,
            ORDER = SoundEvents.BLOCK_NOTE_PLING, DENY = SoundEvents.BLOCK_NOTE_BASS;

    static void play(SoundEvent s) { play(s, 1F); }

    static void play(SoundEvent s, float pitch) {
        if (!Settings.get().clickSound) return;
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(s, pitch));
    }

    /** the selected units answer: villagers "hmm", golems clank, pets yip */
    static void answer(String kind) {
        if (!Settings.get().clickSound) return;
        SoundEvent s;
        switch (kind) {
            case "VILLAGER": case "NPC": s = SoundEvents.ENTITY_VILLAGER_YES; break;
            case "GOLEM": s = SoundEvents.ENTITY_IRONGOLEM_ATTACK; break;
            case "PET": s = SoundEvents.ENTITY_WOLF_AMBIENT; break;
            case "MOUNT": s = SoundEvents.ENTITY_HORSE_AMBIENT; break;
            case "SOLDIER": case "GUARD": s = SoundEvents.ITEM_ARMOR_EQUIP_IRON; break;
            default: s = ORDER;
        }
        Minecraft.getMinecraft().getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(s, 0.9F + (float) Math.random() * 0.2F));
    }
}
