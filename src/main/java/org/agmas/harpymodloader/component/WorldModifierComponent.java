package org.agmas.harpymodloader.component;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.*;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.Modifier;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

public class WorldModifierComponent implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<WorldModifierComponent> KEY = ComponentRegistry
            .getOrCreate(Identifier.of(Harpymodloader.MOD_ID, "modifier"), WorldModifierComponent.class);
    private final World world;
    public HashMap<UUID, ArrayList<Modifier>> modifiers = new HashMap<>();

    public WorldModifierComponent(World world) {
        this.world = world;
    }

    @Override
    public void serverTick() {

    }

    @Deprecated
    public boolean isRole(@NotNull PlayerEntity player, Modifier modifier) {
        return isModifier(player, modifier);
    }

    @Deprecated
    public boolean isRole(@NotNull UUID uuid, Modifier modifier) {
        return isModifier(uuid, modifier);
    }

    public boolean isModifier(@NotNull PlayerEntity player, Modifier modifier) {
        return this.isModifier(player.getUuid(), modifier);
    }

    public boolean isModifier(@NotNull UUID uuid, Modifier modifier) {
        return getModifiers(uuid).contains(modifier);
    }

    public HashMap<UUID, ArrayList<Modifier>> getModifiers() {
        return this.modifiers;
    }

    public ArrayList<Modifier> getModifiers(PlayerEntity player) {
        return this.getModifiers(player.getUuid());
    }

    public ArrayList<Modifier> getModifiers(UUID uuid) {
        synchronized (this.modifiers) {
            if (!modifiers.containsKey(uuid))
                modifiers.put(uuid, new ArrayList<>());
            return this.modifiers.get(uuid);
        }
    }

    public List<UUID> getAllWithModifier(Modifier modifier) {
        List<UUID> ret = new ArrayList<>();
        synchronized (this.modifiers) {
            this.modifiers.forEach((uuid, playerModifier) -> {
                if (playerModifier.contains(modifier)) {
                    ret.add(uuid);
                }
            });
        }
        return ret;
    }

    public void setModifiers(List<UUID> players, Modifier modifier) {

        for (UUID player : players) {
            addModifier(player, modifier);
            this.sync();
        }

    }

    public void removeModifier(UUID player, Modifier modifier) {
        synchronized (this.modifiers) {
            getModifiers(player).remove(modifier);
        }
        this.sync();
    }

    public void addModifier(UUID player, Modifier modifier) {
        getModifiers(player).add(modifier);
        this.sync();
    }

    @Override
    public void readFromNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {

        modifiers.clear();
        for (Modifier modifier : HMLModifiers.MODIFIERS) {
            setModifiers(this.uuidListFromNbt(nbtCompound, modifier.identifier().toString()), modifier);
        }
    }

    @Override
    public void writeToNbt(NbtCompound nbtCompound, RegistryWrapper.WrapperLookup wrapperLookup) {
        synchronized (this.modifiers) {
            for (Modifier modifier : HMLModifiers.MODIFIERS) {
                // 在同步块内直接查找，避免嵌套同步调用
                List<UUID> uuidsWithModifier = new ArrayList<>();
                for (Map.Entry<UUID, ArrayList<Modifier>> entry : this.modifiers.entrySet()) {
                    if (entry.getValue().contains(modifier)) {
                        uuidsWithModifier.add(entry.getKey());
                    }
                }
                nbtCompound.put(modifier.identifier().toString(), this.nbtFromUuidList(uuidsWithModifier));
            }
        }
    }

    public void sync() {
        KEY.sync(this.world);
    }

    @Override
    public void clientTick() {

    }

    private ArrayList<UUID> uuidListFromNbt(NbtCompound nbtCompound, String listName) {
        ArrayList<UUID> ret = new ArrayList();

        for (NbtElement e : nbtCompound.getList(listName, 11)) {
            ret.add(NbtHelper.toUuid(e));
        }

        return ret;
    }

    private NbtList nbtFromUuidList(List<UUID> list) {
        NbtList ret = new NbtList();

        for (UUID player : list) {
            ret.add(NbtHelper.fromUuid(player));
        }

        return ret;
    }

    public ArrayList<Modifier> getDisplayableModifiers(PlayerEntity player) {
        var modifiers = this.getModifiers(player.getUuid());
        modifiers.removeIf((modifier) -> {
            return Harpymodloader.HIDDEN_MODIFIERS.contains(modifier.identifier());
        });
        return modifiers;
    }
}
