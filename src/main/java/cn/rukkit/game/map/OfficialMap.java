/*
 * Copyright 2020-2022 RukkitDev Team and contributors.
 *
 * This project uses GNU Affero General Public License v3.0.You can find this license in the following link.
 * 本项目使用 GNU Affero General Public License v3.0 许可证，你可以在下方链接查看:
 *
 * https://github.com/RukkitDev/Rukkit/blob/master/LICENSE
 */

package cn.rukkit.game.map;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OfficialMap
{
    private static final String[] ALL_MAPS = {
        "[p2]Beach landing (2p) [by hxyy]",
        "[p2]Big Island (2p)",
        "[p2]Dire_Straight (2p) [by uber]",
        "[p2]Fire Bridge (2p) [by uber]",
        "[p2]Hills_(2p)_[By Tstis & KPSS]",
        "[p2]Ice Island (2p)",
        "[p2]Lake (2p)",
        "[p2]Small_Island (2p)",
        "[p2]Two_cold_sides (2p)",
        "[p3]Hercules_(2vs1p) [by_uber]",
        "[p3]King of the Middle (3p)",
        "[p4]Depth charges (4p) [by hxyy]",
        "[p4]Desert (4p)",
        "[p4]Ice Lake (4p) [by hxyy]",
        "[p4]Island freeze (4p) [by hxyy]",
        "[p4]Lava Maze (4p)",
        "[p4]Lava Vortex (4p)",
        "[p4]Nuclear war (4p) [by hxyy]",
        "[p4]Magma Island (4p)",
        "[p4]Islands (4p)",
        "[p6]Shore to Shore (6p)",
        "[p6]Valley Pass (6p)",
        "[p6]Crossing (6p)",
        "[p8]Bridges Over Lava (8p)",
        "[p8]Coastline (8p) [by hxyy]",
        "[p8]Huge Subdivide (8p)",
        "[p8]Interlocked (8p)",
        "[p8]Interlocked Large (8p)",
        "[p8]Isle Ring (8p)",
        "[p8]Large Ice Outcrop (8p)",
        "[p8]Lava Bio-grid(8p)",
        "[p8]Lava Divide(8p)",
        "[p8]Many Islands (8p)",
        "[p8]Random Islands (8p)",
        "[p8]Two Sides (8p)",
        "[p8]Volcano (8p)",
        "[p8]Volcano Crater(8p)",
        "[p8]Tornado eye (8p) [by hxyy]",
        "[z;p10]Enclosed Island (10p)",
        "[z;p10]Kingdoms (10p) [by Vulkan]",
        "[z;p10]Large Lava Divide (10p)",
        "[z;p10]Two Sides Remake (10p)",
        "[z;p10]Valley Arena (10p) [by_uber]",
        "[z;p10]Many Islands Large (10p)",
        "[z;p10]Crossing Large (10p)",
        "[z;p10]Enclosed Island (10p)",
        "[z;p10]Two_Large_Islands_(10p)",
        "[z;p10]Wetlands (10p)"
    };

    private static final String[] ALL_MAPS_NAME = {
        "Beach landing (2p) [by hxyy]",
        "Big Island (2p)",
        "Dire Straight (2p) [by uber]",
        "Fire Bridge (2p) [by uber]",
        "Hills (2p) [By Tstis & KPSS]",
        "Ice Island (2p)",
        "Lake (2p)",
        "Small Island (2p)",
        "Two cold sides (2p)",
        "Hercules (2vs1p) [by uber]",
        "King of the Middle (3p)",
        "Depth charges (4p) [by hxyy]",
        "Desert (4p)",
        "Ice Lake (4p) [by hxyy]",
        "Island freeze (4p) [by hxyy]",
        "Lava Maze (4p)",
        "Lava Vortex (4p)",
        "Nuclear war (4p) [by hxyy]",
        "Magma Island (4p)",
        "Islands (4p)",
        "Shore to Shore (6p)",
        "Valley Pass (6p)",
        "Crossing (6p)",
        "Bridges Over Lava (8p)",
        "Coastline (8p) [by hxyy]",
        "Huge Subdivide (8p)",
        "Interlocked (8p)",
        "Interlocked Large (8p)",
        "Isle Ring (8p)",
        "Large Ice Outcrop (8p)",
        "Lava Bio-grid(8p)",
        "Lava Divide(8p)",
        "Many Islands (8p)",
        "Random Islands (8p)",
        "Two Sides (8p)",
        "Volcano (8p)",
        "Volcano Crater(8p)",
        "Tornado eye (8p) [by hxyy]",
        "Enclosed Island (10p)",
        "Kingdoms (10p) [by Vulkan]",
        "Large Lava Divide (10p)",
        "Two Sides Remake (10p)",
        "Valley Arena (10p) [by uber]",
        "Many Islands Large (10p)",
        "Crossing Large (10p)",
        "Enclosed Island (10p)",
        "Two Large Islands (10p)",
        "Wetlands (10p)"
    };

    /**
     * The arrays used by the existing .maps and .map commands.
     * They are rebuilt from the immutable ALL_MAPS arrays when the server config is loaded.
     */
    public static String[] maps = ALL_MAPS.clone();
    public static String[] mapsName = ALL_MAPS_NAME.clone();

    private static final Pattern PLAYER_COUNT_PATTERN = Pattern.compile("(?:^|[;\\[]|\\s)p(\\d+)(?:[;\\]\\s]|\\s|$)", Pattern.CASE_INSENSITIVE);

    private static int playerCount(String map) {
        Matcher matcher = PLAYER_COUNT_PATTERN.matcher(map);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        // Fallback for the human-readable suffix, e.g. "(10p)".
        Matcher suffix = Pattern.compile("\\((\\d+)p\\)", Pattern.CASE_INSENSITIVE).matcher(map);
        if (suffix.find()) {
            return Integer.parseInt(suffix.group(1));
        }

        return -1;
    }

    /**
     * Filters the official map list by the configured map size range.
     * Custom maps are intentionally not affected.
     */
    public static void applyPlayerCountFilter(int min, int max) {
        if (min < 1) min = 1;
        if (max < min) max = min;

        List<String> filteredMaps = new ArrayList<>();
        List<String> filteredNames = new ArrayList<>();

        for (int i = 0; i < ALL_MAPS.length; i++) {
            int players = playerCount(ALL_MAPS[i]);
            if (players >= min && players <= max) {
                filteredMaps.add(ALL_MAPS[i]);
                filteredNames.add(ALL_MAPS_NAME[i]);
            }
        }

        maps = filteredMaps.toArray(new String[0]);
        mapsName = filteredNames.toArray(new String[0]);
    }
}
