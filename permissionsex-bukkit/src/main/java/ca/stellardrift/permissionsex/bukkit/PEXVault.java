/*
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.stellardrift.permissionsex.bukkit;

import ca.stellardrift.permissionsex.PermissionsEx;
import ca.stellardrift.permissionsex.context.ContextValue;
import ca.stellardrift.permissionsex.subject.CalculatedSubject;
import ca.stellardrift.permissionsex.util.Util;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("deprecation")
class PEXVault extends Permission {
    final PermissionsExPlugin plugin;

    PEXVault(PermissionsExPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return this.plugin.getName();
    }

    @Override
    public boolean isEnabled() {
        return this.plugin.isEnabled();
    }

    @Override
    public boolean hasSuperPermsCompat() {
        return true;
    }

    @Override
    public String[] getGroups() {
        return Iterables.toArray(this.plugin.getGroupSubjects().getAllIdentifiers(), String.class);
    }

    @Override
    public boolean hasGroupSupport() {
        return true;
    }

    private <T> T getUnchecked(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    CalculatedSubject getGroup(String name) {
        return getUnchecked(this.plugin.getGroupSubjects().get(Preconditions.checkNotNull(name, "name")));
    }

    CalculatedSubject getSubject(OfflinePlayer player) {
        return getUnchecked(this.plugin.getUserSubjects().get(Preconditions.checkNotNull(player, "player").getUniqueId().toString()));
    }

    CalculatedSubject getSubject(String player) {
        return getUnchecked(this.plugin.getUserSubjects().get(Preconditions.checkNotNull(player, "player")));
    }


    Set<ContextValue<?>> contextsFrom(@Nullable String world) {
        return world == null ? PermissionsEx.GLOBAL_CONTEXT : ImmutableSet.of(WorldContextDefinition.INSTANCE.createValue(world));
    }

    /**
     * This will replace the world in the given subject's active contexts.
     * Unfortunately, PEX's context system does not map perfectly onto Vault's idea of per-world permissions,
     * so this may not perfectly match cases where the world being queried does not match an online player's current world.
     *
     * @param subject The subject to provide active contexts for
     * @param worldOverride The world to override with, if any
     * @return The appropriate context values
     */
    Set<ContextValue<?>> contextsFrom(CalculatedSubject subject, @Nullable String worldOverride) {
        Set<ContextValue<?>> origContexts = subject.getActiveContexts();
        if (worldOverride == null) {
            return origContexts;
        }

        Optional<Player> checkPlayer = Util.castOptional(subject.getAssociatedObject(), Player.class);

        if (checkPlayer.isPresent() && checkPlayer.get().getWorld().getName().equalsIgnoreCase(worldOverride)) {
            return origContexts;
        }

        origContexts.removeIf(it -> it.getDefinition() == WorldContextDefinition.INSTANCE);
        origContexts.add(WorldContextDefinition.INSTANCE.createValue(worldOverride));
        return origContexts;
    }

    @Override
    public boolean groupHas(String world, String name, String permission) {
        CalculatedSubject subj = getGroup(name);
        return subj.getPermission(contextsFrom(subj, world), permission) > 0;
    }

    @Override
    public boolean groupAdd(final String world, String name, final String permission) {
        return !getGroup(name).data().update(input -> input.setPermission(contextsFrom(world), permission, 1)).isCancelled();
    }

    @Override
    public boolean groupRemove(final String world, String name, final String permission) {
        return !getGroup(name).data().update(input -> input.setPermission(contextsFrom(world), permission, 0)).isCancelled();

    }

    @Override
    public boolean playerHas(String world, OfflinePlayer player, String permission) {
        CalculatedSubject subj = getSubject(player);
        int perm = subj.getPermission(contextsFrom(subj, world), permission);
        if (perm > 0) {
            return true;
        } else if (perm < 0) {
            return false;
        } else {
            return plugin.getManager().getConfig().getPlatformConfig().shouldFallbackOp() && player.isOp();
        }
    }

    @Override
    public boolean playerAdd(final String world, OfflinePlayer player, final String permission) {
        return !getSubject(player).data().update(input -> input.setPermission(contextsFrom(world), permission, 1)).isCancelled();
    }

    @Override
    public boolean playerAddTransient(OfflinePlayer player, String permission) {
        return playerAddTransient(null, player, permission);
    }

    @Override
    public boolean playerAddTransient(Player player, String permission) {
        return playerAddTransient(null, player, permission);
    }

    @Override
    public boolean playerAddTransient(final String worldName, OfflinePlayer player, final String permission) {
        return !getSubject(player).transientData().update(input -> input.setPermission(contextsFrom(worldName), permission, 1)).isCancelled();
    }

    @Override
    public boolean playerRemoveTransient(final String worldName, OfflinePlayer player, final String permission) {
        return !getSubject(player).transientData().update(input -> input.setPermission(contextsFrom(worldName), permission, 0)).isCancelled();
    }

    @Override
    public boolean playerRemove(final String world, OfflinePlayer player, final String permission) {
        return !getSubject(player).data().update(input -> input.setPermission(contextsFrom(world), permission, 0)).isCancelled();
    }

    @Override
    public boolean playerRemoveTransient(Player player, String permission) {
        return playerRemoveTransient(null, player, permission);
    }

    @Override
    public boolean playerRemoveTransient(OfflinePlayer player, String permission) {
        return playerRemoveTransient(null, player, permission);
    }

    @Override
    public boolean playerInGroup(String world, OfflinePlayer player, String group) {
        CalculatedSubject subj = getSubject(player);
        return subj.getParents(contextsFrom(subj, world)).contains(Maps.immutableEntry(PermissionsEx.SUBJECTS_GROUP, group));
    }

    @Override
    public boolean playerAddGroup(final String world, OfflinePlayer player, final String group) {
        return !getSubject(player).data().update(input -> input.addParent(contextsFrom(world), PermissionsEx.SUBJECTS_GROUP, group)).isCancelled();
    }

    @Override
    public boolean playerRemoveGroup(final String world, OfflinePlayer player, final String group) {
        return !getSubject(player).data().update(input -> input.removeParent(contextsFrom(world), PermissionsEx.SUBJECTS_GROUP, group)).isCancelled();
    }

    @Override
    public String[] getPlayerGroups(String world, OfflinePlayer player) {
        CalculatedSubject subj = getSubject(player);
        return subj.getParents(contextsFrom(subj, world)).stream()
                .filter(parent -> parent.getKey().equals(PermissionsEx.SUBJECTS_GROUP))
                .map(Map.Entry::getValue)
                .toArray(String[]::new);
    }

    @Override
    public String getPrimaryGroup(String world, OfflinePlayer player) {
        String[] groups = getPlayerGroups(world, player);
        return groups.length > 0 ? groups[0] : null;
    }

    // -- Deprecated methods

    private OfflinePlayer pFromName(String name) {
        return this.plugin.getServer().getOfflinePlayer(name);
    }

    @Override
    public boolean playerHas(String world, String name, String permission) {
        return playerHas(world, pFromName(name), permission);
    }

    @Override
    public boolean playerAdd(String world, String name, String permission) {
        return playerAdd(world, pFromName(name), permission);
    }

    @Override
    public boolean playerRemove(String world, String name, String permission) {
        return playerRemove(world, pFromName(name), permission);
    }

    @Override
    public boolean playerInGroup(String world, String player, String group) {
        return playerInGroup(world, pFromName(player), group);
    }

    @Override
    public boolean playerAddGroup(String world, String player, String group) {
        return playerAddGroup(world, pFromName(player), group);
    }

    @Override
    public boolean playerRemoveGroup(String world, String player, String group) {
        return playerRemoveGroup(world, pFromName(player), group);
    }

    @Override
    public String[] getPlayerGroups(String world, String player) {
        return getPlayerGroups(world, pFromName(player));
    }

    @Override
    public String getPrimaryGroup(String world, String player) {
        return getPrimaryGroup(world, pFromName(player));
    }
}
