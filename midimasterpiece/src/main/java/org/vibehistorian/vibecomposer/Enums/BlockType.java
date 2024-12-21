package org.vibehistorian.vibecomposer.Enums;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.vibehistorian.vibecomposer.MelodyUtils;
import org.vibehistorian.vibecomposer.OMNI;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public enum BlockType {
    SCALEY(100),
    NEIGHBORY(100),
    ARPY(100),
    CHORDY(100),
    WAVY(100),
    INTERVAL(100),
    NOTE(100);

    public final int defaultChance;
    public List<Integer[]> blocks = new ArrayList<>();

    BlockType(int chance) {
        defaultChance = chance;
    }


    public static final Map<Integer, List<Pair<Integer, Integer[]>>> BLOCK_CHANGE_MAP;
    public static final Map<Integer, Set<Integer>> AVAILABLE_BLOCK_CHANGES_PER_TYPE = new HashMap<>();

    private static Integer[] block(Integer... notePositions) {
        return notePositions;
    }

    public static Integer blockChange(Integer[] block) {
        return block[block.length - 1] - block[0];
    }

    static {
        SCALEY.blocks.add(block(0, 1, 2));
        SCALEY.blocks.add(block(0, 1, 2, 3));
        SCALEY.blocks.add(block(0, 1, 4));
        SCALEY.blocks.add(block(0, 1, 2, 1));
        SCALEY.blocks.add(block(0, 4, 2));
        SCALEY.blocks.add(block(0, 1, 2, 0));
        SCALEY.blocks.add(block(0, 1, 2, 4));
        SCALEY.blocks.add(block(0, 0, 0));

        NEIGHBORY.blocks.add(block(0, -1, 0));
        NEIGHBORY.blocks.add(block(0, 1, 0, 1));
        NEIGHBORY.blocks.add(block(0, 1, -1));
        NEIGHBORY.blocks.add(block(0, -1, 0, 1));
        NEIGHBORY.blocks.add(block(0, -1, 2));
        NEIGHBORY.blocks.add(block(0, 1, -1, 0));
        NEIGHBORY.blocks.add(block(0, -1, 0, 2));
        NEIGHBORY.blocks.add(block(0, 1, 3, 2));
        NEIGHBORY.blocks.add(block(0, 0, -1, 0));


        CHORDY.blocks.add(block(0, 2, 4));
        CHORDY.blocks.add(block(0, 4, 2));
        CHORDY.blocks.add(block(0, 4, 2, 7));
        CHORDY.blocks.add(block(0, 7, 4, 2));

        ARPY.blocks.add(block(0, 2, 0, 2));
        ARPY.blocks.add(block(0, 2, 1));
        ARPY.blocks.add(block(0, 2, 4, 2));
        ARPY.blocks.add(block(0, 2, 1, 3));
        ARPY.blocks.add(block(0, 2, 3));
        ARPY.blocks.add(block(0, 3, 1, 2));
        ARPY.blocks.add(block(0, 1, 4, 5));
        ARPY.blocks.add(block(0, 1, 7, 6));
        ARPY.blocks.add(block(0, 1, 6, 7));

        WAVY.blocks.add(block(0, -2, 3, 4));
        WAVY.blocks.add(block(0, -2, 2, 6));
        WAVY.blocks.add(block(0, -2, 3, 6));
        WAVY.blocks.add(block(0, -2, -1, 2));
        WAVY.blocks.add(block(0, -1, -2, 1));
        WAVY.blocks.add(block(0, 1, -1, 2));
        WAVY.blocks.add(block(0, 2, -1, -2));
        WAVY.blocks.add(block(0, -3, -2, -1));

        INTERVAL.blocks.add(block(0, 0));
        INTERVAL.blocks.add(block(0, 1));
        INTERVAL.blocks.add(block(0, 2));
        INTERVAL.blocks.add(block(0, 3));
        INTERVAL.blocks.add(block(0, 4));
        INTERVAL.blocks.add(block(0, 5));
        INTERVAL.blocks.add(block(0, 7));

        NOTE.blocks.add(block(0));

        List<Pair<Integer, Integer[]>> allBlocks = new ArrayList<>();
        for (BlockType blockType : BlockType.values()) {
            blockType.blocks.forEach(e -> allBlocks.add(Pair.of(blockType.ordinal(), e)));
            AVAILABLE_BLOCK_CHANGES_PER_TYPE.put(blockType.ordinal(),
                    blockType.blocks.stream().map(BlockType::blockChange).collect(Collectors.toSet()));
        }

        BLOCK_CHANGE_MAP = allBlocks.stream()
                .collect(Collectors.groupingBy(e -> blockChange(e.getRight())));
    }

    public static List<Integer[]> getBlocksForType(Integer type) {
        if (type == null) {
            List<Integer[]> blocks = new ArrayList<>();
            for (BlockType blockType : BlockType.values()) {
                blocks.addAll(blockType.blocks);
            }
            return blocks;
        }

        return BlockType.values()[type].blocks;
    }

    public static int blockOfList(Integer[] block) {
        if (block == null) {
            throw new IllegalArgumentException("Block is NULL!");
        }
        Integer[] invertedBlock = inverse(block);
        for (BlockType blockType : BlockType.values()) {
            if (containsBlock(blockType.blocks, block, invertedBlock)) {
                return blockType.ordinal();
            }
        }
        throw new IllegalArgumentException("Unknown block: " + StringUtils.join(block, ","));
    }

    public static Integer[] inverse(Integer[] block) {
        Integer[] newBlock = new Integer[block.length];
        for (int i = 0; i < block.length; i++) {
            newBlock[i] = block[i] * -1;
        }
        return newBlock;
    }

    public static Integer getWeightedType(List<Integer> limitedTypes, List<Integer> weights, int target) {
        Integer[] actualWeights = new Integer[limitedTypes.size()];
        if (limitedTypes.size() != weights.size()) {
            int count = 0;
            for (Integer i : limitedTypes) {
                actualWeights[count++] = weights.get(i);
            }
        }
        int[] normalizedWeights = MelodyUtils.normalizedCumulativeWeights(actualWeights);
        return (Integer) OMNI.getWeightedValue(limitedTypes.toArray(), target, normalizedWeights);
    }

    private static boolean containsBlock(List<Integer[]> blocks, Integer[] block,
                                         Integer[] invertedBlock) {
        for (Integer[] blk : blocks) {
            if (block.length != blk.length) {
                continue;
            }
            boolean isDirectBlock = true;
            boolean isInvertedBlock = true;
            for (int j = 0; j < block.length; j++) {
                if (!block[j].equals(blk[j])) {
                    isDirectBlock = false;
                } else if (!invertedBlock[j].equals(blk[j])) {
                    isInvertedBlock = false;
                }
            }
            if (isDirectBlock || isInvertedBlock) {
                return true;
            }
        }
        return false;
    }
}
