package com.boydti.fawe.command;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.config.BBC;
import com.boydti.fawe.object.FaweCommand;
import com.boydti.fawe.object.FaweLocation;
import com.boydti.fawe.object.FawePlayer;
import com.boydti.fawe.object.RegionWrapper;
import com.boydti.fawe.object.RunnableVal;
import com.boydti.fawe.object.changeset.DiskStorageHistory;
import com.boydti.fawe.util.MainUtil;
import com.boydti.fawe.util.MathMan;
import com.boydti.fawe.util.SetQueue;
import com.boydti.fawe.util.TaskManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.world.World;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class Rollback extends FaweCommand {

    public Rollback() {
        super("fawe.rollback");
    }

    @Override
    public boolean execute(final FawePlayer player, final String... args) {
        if (args.length < 1) {
            BBC.COMMAND_SYNTAX.send(player, "/frb <info|undo> u:<uuid> r:<radius> t:<time>");
            return false;
        }
        World world = player.getWorld();
        switch (args[0]) {
            default: {
                BBC.COMMAND_SYNTAX.send(player, "/frb info u:<uuid> r:<radius> t:<time>");
                return false;
            }
            case "i":
            case "info": {
                if (args.length < 2) {
                    BBC.COMMAND_SYNTAX.send(player, "/frb <info|undo> u:<uuid> r:<radius> t:<time>");
                    return false;
                }
                player.deleteMeta("rollback");
                final FaweLocation origin = player.getLocation();
                rollback(player, Arrays.copyOfRange(args, 1, args.length), new RunnableVal<List<DiskStorageHistory>>() {
                    @Override
                    public void run(List<DiskStorageHistory> edits) {
                        long total = 0;
                        player.sendMessage("&d=== Edits ===");
                        for (DiskStorageHistory edit : edits) {
                            int[] headerAndFooter = edit.readHeaderAndFooter(new RegionWrapper(origin.x, origin.x, origin.z, origin.z));
                            RegionWrapper region = new RegionWrapper(headerAndFooter[0], headerAndFooter[2], headerAndFooter[1], headerAndFooter[3]);
                            int dx = region.distanceX(origin.x);
                            int dz = region.distanceZ(origin.z);
                            String name = Fawe.imp().getName(edit.getUUID());
                            long seconds = (System.currentTimeMillis() - edit.getBDFile().lastModified()) / 1000;
                            total += edit.getBDFile().length();
                            player.sendMessage(name + " : " + dx + "," + dz + " : " + MainUtil.secToTime(seconds));
                        }
                        player.sendMessage("&d=============");
                        player.sendMessage("&dSize: " + (((double) (total / 1024)) / 1000) + "MB");
                        player.sendMessage("&dTo rollback: /frb undo");
                        player.sendMessage("&d=============");
                        player.setMeta("rollback", edits);
                    }
                });
                break;
            }
            case "undo":
            case "revert": {
                final List<DiskStorageHistory> edits = (List<DiskStorageHistory>) player.getMeta("rollback");
                player.deleteMeta("rollback");
                if (edits == null) {
                    BBC.COMMAND_SYNTAX.send(player, "/frb info u:<uuid> r:<radius> t:<time>");
                    return false;
                }
                final Runnable task = new Runnable() {
                    @Override
                    public void run() {
                        if (edits.size() == 0) {
                            player.sendMessage("&d" + BBC.PREFIX.s() + " Rollback complete!");
                            return;
                        }
                        DiskStorageHistory edit = edits.remove(0);
                        player.sendMessage("&d" + edit.getBDFile());
                        EditSession session = edit.toEditSession(null);
                        session.undo(session);
                        edit.deleteFiles();
                        SetQueue.IMP.addTask(this);
                    }
                };
                TaskManager.IMP.async(task);
            }
        }
        return true;
    }

    public void rollback(final FawePlayer player, final String[] args, final RunnableVal<List<DiskStorageHistory>> result) {
        TaskManager.IMP.async(new Runnable() {
            @Override
            public void run() {
                UUID user = null;
                int radius = Integer.MAX_VALUE;
                long time = Long.MAX_VALUE;
                for (int i = 0; i < args.length; i++) {
                    String[] split = args[i].split(":");
                    if (split.length != 2) {
                        BBC.COMMAND_SYNTAX.send(player, "/frb <info|undo> u:<uuid> r:<radius> t:<time>");
                        return;
                    }
                    switch (split[0].toLowerCase()) {
                        case "username":
                        case "user":
                        case "u": {
                            try {
                                if (split[1].length() > 16) {
                                    user = UUID.fromString(split[1]);
                                } else {
                                    user = Fawe.imp().getUUID(split[1]);
                                }
                            } catch (IllegalArgumentException e) {}
                            if (user == null) {
                                player.sendMessage("&dInvalid user: " + split[1]);
                                return;
                            }
                            break;
                        }
                        case "r":
                        case "radius": {
                            if (!MathMan.isInteger(split[1])) {
                                player.sendMessage("&dInvalid radius: " + split[1]);
                                return;
                            }
                            radius = Integer.parseInt(split[1]);
                            break;
                        }
                        case "t":
                        case "time": {
                            time = MainUtil.timeToSec(split[1]) * 1000;
                            break;
                        }
                        default: {
                            BBC.COMMAND_SYNTAX.send(player, "/frb <info|undo> u:<uuid> r:<radius> t:<time>");
                            return;
                        }
                    }
                }
                FaweLocation origin = player.getLocation();
                List<DiskStorageHistory> edits = MainUtil.getBDFiles(origin, user, radius, time);
                if (edits.size() == 0) {
                    player.sendMessage("No edits found!");
                    return;
                }
                result.run(edits);
            }
        });
    }
}