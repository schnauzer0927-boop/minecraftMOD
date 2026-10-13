"""Rail-free, open boarding terminal for the Stage 3 interior study.

Tokyo's old station interior has not been verified from an interior photograph.
The white / grey / blue terminal, glass control room and launch frame are an
explicit creative completion of the user's requested atmosphere.  Coordinates
are project-relative Minecraft design values, not measured building dimensions.

All writes use Model's protected-volume / exterior-collision checks.  This
module never places air, rails, vehicles, entities or block-entity signs.
"""
from __future__ import annotations

import math

WHITE = 'minecraft:white_concrete'
QUARTZ = 'minecraft:smooth_quartz'
FLOOR = 'minecraft:smooth_stone'
PALE = 'minecraft:light_gray_concrete'
BLUE = 'minecraft:blue_concrete'
LIGHT_BLUE = 'minecraft:light_blue_concrete'
CYAN = 'minecraft:cyan_concrete'
LIGHT = 'minecraft:sea_lantern'
GLASS = 'minecraft:light_blue_stained_glass'
PANE_NS = ('minecraft:light_blue_stained_glass_pane['
           'east=false,north=true,south=true,waterlogged=false,west=false]')


def station_geometry(config):
    """Return the actual platform, doorway and open-lane construction plan.

    Raising ``station_floor_y`` moves the platforms, staff room, floor guides
    and doorway sills together.  The ceiling is separately configurable.  The
    caller owns matching ride / pedestrian reservations for the chosen height.
    """
    floor = int(config.get('station_floor_y', 4))
    ceiling = int(config.get('station_ceiling_y', 20))
    x0 = int(config.get('station_min_x', 88))
    x1 = int(config.get('station_max_x', 132))
    z0 = int(config.get('station_min_z', 122))
    z1 = int(config.get('station_max_z', 143))
    lane0 = int(config.get('station_lane_min_x', 106))
    lane1 = int(config.get('station_lane_max_x', 114))
    if ceiling - floor < 15:
        raise ValueError('Station ceiling needs 15 blocks above its floor')
    if lane0 - x0 < 15 or x1 - lane1 < 15 or z1 - z0 < 20:
        raise ValueError('Station terminal needs the planned side-platform space')
    cx = (lane0 + lane1) // 2
    queue_z = z0 + 10
    staff_z1 = z0 + 7
    gate_bands = [[z0 + 3, z0 + 7], [z0 + 10, z1 - 4]]
    return {
        'coordinate_space': 'project_relative_xyz',
        'confidence': 'creative_completion',
        'historical_interior_verified': False,
        'hall': {'min': [x0, floor - 2, z0], 'max': [x1, ceiling, z1]},
        'floor_y': floor,
        'passenger_foot_y': floor + 1,
        'recommended_rail_center_y': floor + 2,
        'ceiling_y': ceiling,
        'lane': {'min': [lane0, floor + 1, z0 - 2],
                 'max': [lane1, floor + 11, z1]},
        'platforms': [
            {'id': 'boarding_west', 'min': [x0 + 2, floor, z0 + 1],
             'max': [lane0 - 1, floor, z1 - 2],
             'floor_including_perimeter': {'min': [x0 + 1, floor, z0],
                                           'max': [lane0 - 1, floor, z1]}},
            {'id': 'unloading_east', 'min': [lane1 + 1, floor, z0 + 1],
             'max': [x1 - 2, floor, z1 - 2],
             'floor_including_perimeter': {'min': [lane1 + 1, floor, z0],
                                           'max': [x1 - 1, floor, z1]}},
        ],
        'control_room': {'min': [x0, floor, z0 + 1],
                         'max': [x0 + 6, floor + 9, staff_z1 + 1]},
        'queue_door': {'min': [x0, floor + 1, queue_z],
                       'max': [x0, floor + 4, queue_z + 4],
                       'center': [x0, floor + 1, queue_z + 2]},
        'exit_door': {'min': [x1 - 9, floor + 1, z1],
                      'max': [x1 - 4, floor + 4, z1],
                      'center': [x1 - 7, floor + 1, z1]},
        'boarding_openings': [
            {'x': lane0 - 1, 'z_ranges_inclusive': gate_bands},
            {'x': lane1 + 1, 'z_ranges_inclusive': gate_bands},
        ],
        'launch_frame': {'min': [lane0 - 3, floor + 1, z0],
                         'max': [lane1 + 3, ceiling, z0 + 1],
                         'beam_min_y': ceiling - 3},
        'wayfinding': {'boarding_color': 'light_blue', 'exit_color': 'cyan',
                       'boarding_arrow_points_north': True,
                       'exit_arrow_points_south': True},
        'rail_blocks_generated': False,
        'station_center_x': cx,
    }


def _wall_with_opening(model, fixed, start, end, floor, ceiling,
                       opening, axis, group):
    """A thin clean wall; omit doorway voxels instead of clearing other work."""
    for v in range(start, end + 1):
        for y in range(floor + 1, ceiling):
            if opening and opening[0] <= v <= opening[1] and y <= floor + 4:
                continue
            block = PALE if y <= floor + 2 else WHITE
            if y == floor + 3:
                block = LIGHT_BLUE
            p = (fixed, y, v) if axis == 'x' else (v, y, fixed)
            model.set(*p, block, group)


def _floor_arrow(model, x, z, floor, direction, block, group):
    """Small inlaid arrow made of blocks, without fabricated logo or text."""
    if direction in {'north', 'south'}:
        step = -1 if direction == 'north' else 1
        for dx in range(-2, 3):
            model.set(x + dx, floor, z - step * abs(dx), block, group)
        model.line((x, floor, z - step * 4), (x, floor, z), block, group)
    else:
        for dz in range(-2, 3):
            model.set(x - abs(dz), floor, z + dz, block, group)
        model.line((x - 4, floor, z), (x, floor, z), block, group)


def generate_station(model, config) -> None:
    """Build side platforms and a luminous station without occupying its lane."""
    plan = station_geometry(config)
    hall = plan['hall']
    x0, _, z0 = hall['min']
    x1, ceiling, z1 = hall['max']
    floor = plan['floor_y']
    lane0 = plan['lane']['min'][0]
    lane1 = plan['lane']['max'][0]
    cx = plan['station_center_x']
    group_floor = 'station_platforms'
    group_shell = 'station_shell'
    group_light = 'station_lighting'
    group_control = 'station_control_room'
    group_detail = 'station_wayfinding'
    group_frame = 'station_launch_frame'

    # Broad pale platforms frame a lower grey channel.  Only the bed below the
    # clear lane is placed: this is a floor datum, not a rail or coaster part.
    model.box((lane0, floor - 2, z0), (lane1, floor - 2, z1),
              'minecraft:gray_concrete', group_floor)
    for side, a, b in [('west', x0 + 1, lane0 - 1),
                       ('east', lane1 + 1, x1 - 1)]:
        model.box((a, floor - 2, z0), (b, floor - 1, z1), WHITE, group_floor)
        model.box((a, floor, z0), (b, floor, z1), FLOOR, group_floor)
        # Fine tile seams and quartz edging make the deck read as a terminal.
        for x in range(a, b + 1):
            for z in range(z0 + 1, z1):
                if (x - a) % 6 == 0 and (z - z0) % 5 == 0:
                    model.set(x, floor, z, PALE, group_floor)
        inner = b if side == 'west' else a
        model.line((inner, floor, z0), (inner, floor, z1), QUARTZ, group_floor)
        warning_x = inner - 1 if side == 'west' else inner + 1
        model.line((warning_x, floor, z0 + 1),
                   (warning_x, floor, z1 - 1), LIGHT_BLUE, group_detail)
        # Short low glazed islands leave two generous, walk-through boarding
        # openings on each platform.  Never fill the central car channel.
        openings = plan['boarding_openings'][0]['z_ranges_inclusive']
        for z in range(z0 + 1, z1 - 1):
            if any(lo <= z <= hi for lo, hi in openings):
                continue
            model.set(inner, floor + 1, z, PANE_NS, group_detail)
            if z in {z0 + 1, z0 + 8, z1 - 3}:
                model.set(inner, floor + 1, z, QUARTZ, group_detail)

    # The hall is deliberately shallow enough to remain inside the unchanged
    # circular exterior.  The side entry and south exit are true block gaps.
    q = plan['queue_door']
    _wall_with_opening(model, x0, z0, z1, floor, ceiling,
                       (q['min'][2], q['max'][2]), 'x', group_shell)
    _wall_with_opening(model, x1, z0, z1, floor, ceiling,
                       None, 'x', group_shell)
    e = plan['exit_door']
    model.line((x0, floor, q['min'][2]), (x0, floor, q['max'][2]),
               QUARTZ, group_floor)
    for a, b in [(x0, lane0 - 1), (lane1 + 1, x1)]:
        _wall_with_opening(model, z1, a, b, floor, ceiling,
                           (e['min'][0], e['max'][0]), 'z', group_shell)
    for a, b in [(x0, lane0 - 4), (lane1 + 4, x1)]:
        _wall_with_opening(model, z0, a, b, floor, ceiling,
                           None, 'z', group_shell)

    # Quiet wall rhythm: one-block pilasters at the edges, recessed blue panels
    # and light coves.  No heavy columns or machinery occupy the public floor.
    for x in [x0 + 1, x1 - 1]:
        for z in [z0 + 1, z0 + 9, z1 - 1]:
            if x == x0 + 1 and q['min'][2] <= z <= q['max'][2]:
                continue
            model.line((x, floor + 5, z), (x, ceiling - 1, z),
                       QUARTZ, group_shell)
        model.line((x, ceiling - 2, z0 + 2),
                   (x, ceiling - 2, z1 - 2), LIGHT, group_light)
    # Tall glossy wall bays retain large calm white areas around them.
    for x in [x0, x1]:
        for start, end in [(z0 + 9, z0 + 15), (z0 + 17, z1 - 2)]:
            if start > end:
                continue
            model.box((x, floor + 6, start), (x, floor + 10, end),
                      GLASS, group_shell)
            model.line((x, floor + 5, start), (x, floor + 5, end),
                       QUARTZ, group_shell)

    # A high white ceiling with a translucent blue lightwell and long inset
    # lamps gives a spacious passenger terminal rather than an industrial bay.
    model.box((x0, ceiling, z0), (x1, ceiling, z1), WHITE, group_shell)
    for z in [z0, z0 + 10, z1]:
        model.line((x0 + 1, ceiling, z), (x1 - 1, ceiling, z),
                   PALE, group_shell)
    model.box((lane0, ceiling, z0 + 4), (lane1, ceiling, z1 - 4),
              GLASS, group_shell)
    for x in [lane0 - 1, lane1 + 1]:
        model.line((x, ceiling, z0 + 4), (x, ceiling, z1 - 4),
                   LIGHT, group_light)
    for z in [z0 + 4, z0 + 12, z1 - 3]:
        for a, b in [(x0 + 5, lane0 - 3), (lane1 + 3, x1 - 5)]:
            model.line((a, ceiling - 1, z), (b, ceiling - 1, z),
                       LIGHT, group_light)
            model.line((a - 1, ceiling - 1, z - 1),
                       (b + 1, ceiling - 1, z - 1), QUARTZ, group_shell)

    # The sculpted launch arch is the focal point from the waiting platform.
    # All cross-lane members stay above the reserved car / passenger envelope.
    beam = plan['launch_frame']['beam_min_y']
    for a, b in [(lane0 - 3, lane0 - 1), (lane1 + 1, lane1 + 3)]:
        model.box((a, floor + 1, z0), (b, beam - 1, z0 + 1),
                  QUARTZ, group_frame)
        glow_x = b if b < lane0 else a
        model.line((glow_x, floor + 2, z0 + 2),
                   (glow_x, beam - 2, z0 + 2), LIGHT, group_light)
    model.box((lane0 - 3, beam, z0), (lane1 + 3, ceiling - 1, z0 + 1),
              QUARTZ, group_frame)
    model.box((lane0 - 2, ceiling - 2, z0 + 2),
              (lane1 + 2, ceiling - 1, z0 + 2), BLUE, group_frame)
    model.line((lane0, beam, z0 + 2), (lane1, beam, z0 + 2),
               LIGHT, group_light)
    # Small abstract white star, explicitly a new motif rather than a logo.
    model.set(cx, ceiling - 1, z0 + 3, LIGHT, group_detail)
    for dx in [-1, 1]:
        model.set(cx + dx, ceiling - 1, z0 + 3, WHITE, group_detail)
    model.set(cx, ceiling - 2, z0 + 3, WHITE, group_detail)

    # Glass-fronted staff room at the northwest edge.  Console pieces stay
    # entirely inside this room; the boarding floor has no machinery clutter.
    rx0, rz0 = x0 + 1, z0 + 1
    rx1, rz1 = x0 + 6, z0 + 8
    model.box((rx0, floor, rz0), (rx1, floor, rz1), QUARTZ, group_control)
    for z in range(rz0, rz1 + 1):
        model.set(rx1, floor + 1, z, PALE, group_control)
        model.box((rx1, floor + 2, z), (rx1, floor + 7, z),
                  GLASS, group_control)
        model.set(rx1, floor + 8, z, QUARTZ, group_control)
    # Staff doorway faces south and does not intrude on the west queue entry.
    for x in range(rx0, rx1 + 1):
        staff_door = rx0 + 1 <= x <= rx0 + 2
        for y in range(floor + 1, floor + 9):
            if staff_door and y <= floor + 3:
                continue
            material = GLASS if floor + 2 <= y <= floor + 7 else QUARTZ
            model.set(x, y, rz1, material, group_control)
    model.box((rx0, floor + 9, rz0), (rx1, floor + 9, rz1),
              WHITE, group_control)
    model.line((rx0 + 2, floor + 8, rz0 + 2),
               (rx1 - 1, floor + 8, rz0 + 2), LIGHT, group_light)
    for x in range(rx0 + 1, rx1):
        model.set(x, floor + 1, rz0 + 1, QUARTZ, group_control)
        model.set(x, floor + 2, rz0 + 1,
                  'minecraft:quartz_stairs[facing=south,half=bottom,'
                  'shape=straight,waterlogged=false]', group_control)
        if x % 2 == 0:
            model.set(x, floor + 3, rz0, LIGHT_BLUE, group_control)
            model.set(x, floor + 4, rz0, BLUE, group_control)
    model.box((rx0 + 3, floor + 1, rz1 - 3),
              (rx0 + 3, floor + 1, rz1 - 3),
              'minecraft:quartz_stairs[facing=north,half=bottom,'
              'shape=straight,waterlogged=false]', group_control)

    # Clear, quiet floor wayfinding: queue line enters through the west gap,
    # then turns toward the launch portal.  Unloading leads to its own exit.
    guide_x = lane0 - 5
    qz = q['center'][2]
    model.line((x0 + 1, floor, qz), (guide_x, floor, qz),
               LIGHT_BLUE, group_detail)
    model.line((guide_x, floor, z0 + 5), (guide_x, floor, z1 - 3),
               LIGHT_BLUE, group_detail)
    _floor_arrow(model, guide_x, z0 + 6, floor, 'north', BLUE, group_detail)
    _floor_arrow(model, guide_x - 1, qz, floor, 'east', BLUE, group_detail)
    exit_x = e['center'][0]
    model.line((exit_x, floor, z0 + 10), (exit_x, floor, z1),
               CYAN, group_detail)
    _floor_arrow(model, exit_x, z1 - 2, floor, 'south', CYAN, group_detail)
    model.line((e['min'][0], floor + 5, z1 - 1),
               (e['max'][0], floor + 5, z1 - 1), LIGHT_BLUE, group_detail)
    model.line((e['min'][0], floor + 6, z1 - 1),
               (e['max'][0], floor + 6, z1 - 1), LIGHT, group_light)

    # A small circular blue observation motif on the east wall is visual
    # interest, with no protruding service equipment in the exit walkway.
    motif_z, motif_y = z0 + 10, floor + 9
    for dz in range(-3, 4):
        for dy in range(-3, 4):
            radius = math.hypot(dz, dy)
            if radius <= 3.1:
                block = QUARTZ if radius > 2.25 else BLUE
                if abs(dz) + abs(dy) == 1:
                    block = LIGHT_BLUE
                model.set(x1 - 1, motif_y + dy, motif_z + dz,
                          block, group_detail)
    model.set(x1 - 1, motif_y, motif_z, LIGHT, group_light)
