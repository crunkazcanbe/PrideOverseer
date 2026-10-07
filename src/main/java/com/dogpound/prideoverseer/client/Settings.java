package com.dogpound.prideoverseer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;

/** Every Pride Overseer option (requested feature). config/prideoverseer-client.json */
public final class Settings {
    // camera
    public float startPitch = 55F, startDistance = 40F, minDistance = 6F, maxDistance = 220F;
    public float panSpeed = 1F, rotateSpeed = 1F, zoomSpeed = 1F;
    public boolean edgePan = true, invertDrag = false, invertZoom = false, smoothCamera = true, followSelected = false, keepHeight = true;
    public int edgePanMargin = 6;
    public float fov = 70F;
    // what the view shows
    public boolean rings = true, healthBars = true, names = true, nameOnlyHover = false, orderLines = true, orderFlags = true;
    public boolean villageBorders = true, villageNames = true, rallyFlags = true, offscreenArrows = true, icons = true;
    public boolean showVillagers = true, showGuards = true, showGolems = true, showPets = true, showMounts = true, showSoldiers = true,
            showWorkers = true, showNpcs = true, showAnimals = true, showHostiles = false;
    public float labelScale = 1F;
    public int ringStyle = 0;                    // 0 glow ring, 1 thin, 2 square
    // panels
    public boolean topBar = true, villagePanel = true, unitCard = true, commandBar = true, minimap = true, messageLog = true, hints = true;
    public int minimapSize = 140;
    public int minimapZoom = 2;                  // blocks per pixel
    public boolean minimapRotate = false;
    public int panelOpacity = 80;
    // controls
    public boolean rightClickMoves = true, doubleClickSelectsType = true, shiftQueues = true, ctrlGroups = true, clickSound = true;
    public String formation = "box";              // box line column circle wedge loose
    public int guardRadius = 10, wanderRadius = 12;
    public boolean showBody = false;            // see your own body down there
    public boolean hideHudOnEnter = true, pauseInSingleplayer = false, autoSelectOnEnter = false;
    // look
    public int accent = 0xFFF5A9B8;
    public boolean pride = true;                 // rainbow touches on rings and bars

    private static final File FILE = new File("config/prideoverseer-client.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings cur;

    public static Settings get() {
        if (cur == null) {
            if (FILE.isFile()) try (Reader r = new FileReader(FILE)) { cur = GSON.fromJson(r, Settings.class); } catch (Throwable ignored) { }
            if (cur == null) cur = new Settings();
        }
        return cur;
    }

    public void save() { try { FILE.getParentFile().mkdirs(); try (Writer w = new FileWriter(FILE)) { GSON.toJson(this, w); } } catch (Throwable ignored) { } }

    public static void reset() { cur = new Settings(); cur.save(); }

    public boolean shows(String kind) {
        switch (kind) {
            case "VILLAGER": return showVillagers; case "GUARD": return showGuards; case "GOLEM": return showGolems; case "PET": return showPets;
            case "MOUNT": return showMounts; case "SOLDIER": return showSoldiers; case "WORKER": return showWorkers; case "NPC": return showNpcs;
            case "HOSTILE": return showHostiles; default: return showAnimals;
        }
    }
}
