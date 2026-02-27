package com.minaret;

/**
 * Marker interface for blocks that participate in a warding column.
 * A warding column is a contiguous vertical stack of WardingPostBlock
 * and/or TeleporterInhibitorBlock. The topmost block entity in the column
 * owns the computed radii for the whole column.
 */
public interface WardingColumnBlock {}
