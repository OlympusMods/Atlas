package com.github.olympus.atlas.core.waypoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Icons {
    private static final Map<String, String[]> ICONS = new LinkedHashMap<>();

    static {
        ICONS.put("dot", new String[] {
            "..###..",
            ".#####.",
            "#######",
            "#######",
            "#######",
            ".#####.",
            "..###.."});
        ICONS.put("diamond", new String[] {
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            ".#####.",
            "..###..",
            "...#..."});
        ICONS.put("square", new String[] {
            "#######",
            "#######",
            "##...##",
            "##...##",
            "##...##",
            "#######",
            "#######"});
        ICONS.put("cross", new String[] {
            "##...##",
            "###.###",
            ".#####.",
            "..###..",
            ".#####.",
            "###.###",
            "##...##"});
        ICONS.put("home", new String[] {
            "...#...",
            "..###..",
            ".#####.",
            "#######",
            ".#####.",
            ".##.##.",
            ".##.##."});
        ICONS.put("flag", new String[] {
            ".#####.",
            ".######",
            ".#####.",
            ".##....",
            ".#.....",
            ".#.....",
            "###...."});
        ICONS.put("star", new String[] {
            "...#...",
            "...#...",
            "#######",
            ".#####.",
            "..###..",
            ".##.##.",
            "##...##"});
        ICONS.put("skull", new String[] {
            ".#####.",
            "#######",
            "#.###.#",
            "#.###.#",
            "#######",
            ".#.#.#.",
            ".#.#.#."});
        ICONS.put("pickaxe", new String[] {
            ".####..",
            "....##.",
            "...#.##",
            "..#...#",
            ".#....#",
            "#......",
            "......."});
        ICONS.put("chest", new String[] {
            ".......",
            "#######",
            "#.....#",
            "###.###",
            "#..#..#",
            "#.....#",
            "#######"});
        ICONS.put("portal", new String[] {
            "#######",
            "#.....#",
            "#.###.#",
            "#.###.#",
            "#.###.#",
            "#.....#",
            "#######"});
        ICONS.put("heart", new String[] {
            ".##.##.",
            "#######",
            "#######",
            "#######",
            ".#####.",
            "..###..",
            "...#..."});
        ICONS.put("tree", new String[] {
            "..###..",
            ".#####.",
            "#######",
            "#######",
            ".#####.",
            "...#...",
            "..###.."});
        ICONS.put("anchor", new String[] {
            "..###..",
            "..#.#..",
            "..###..",
            "...#...",
            "#..#..#",
            "#######",
            ".#####."});
        ICONS.put("warning", new String[] {
            "...#...",
            "..###..",
            "..#.#..",
            ".##.##.",
            ".#####.",
            "###.###",
            "#######"});
        ICONS.put("tower", new String[] {
            "#.#.#.#",
            "#######",
            ".#####.",
            ".##.##.",
            ".#####.",
            ".##.##.",
            "#######"});
    }

    private Icons() {
    }

    public static List<String> names() {
        return List.copyOf(ICONS.keySet());
    }

    public static boolean exists(String name) {
        return ICONS.containsKey(name);
    }

    public static String[] rows(String name) {
        String[] rows = ICONS.get(name);
        return rows == null ? ICONS.get("dot") : rows;
    }
}
