package dev.hytalemodding.impulse.core.crucible;

import java.util.List;
import java.util.Set;

/**
 * Backend-neutral description of one Crucible suite.
 */
record CrucibleSuite(
    String id,
    String name,
    String description,
    Set<String> tags,
    List<CrucibleTestCase> tests) {
}
