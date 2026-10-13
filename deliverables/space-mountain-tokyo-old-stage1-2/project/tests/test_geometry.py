"""Public geometry invariants: usable air, complete shell and aligned parts."""
import json
import math
from pathlib import Path
import tempfile
import unittest

from generator.clearance import read_user_areas
from generator.exterior import roof_height
from generator.geometry import Model
from generator.main import ROOT,build_model,config_at
from generator.validate_schem import read_schem


class ConstructionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.c=config_at(ROOT/'config.json')
        cls.model,cls.route,cls.areas=build_model(cls.c)

    def test_default_construction_never_occupies_reservations(self):
        conflicts=[p for p in self.model.blocks if self.model.is_protected(*p)]
        self.assertEqual([],conflicts)

    def test_roof_has_no_unintended_sky_holes(self):
        c=self.c
        missing=[]
        for x in range(c['center_x']-c['roof_radius'],c['center_x']+c['roof_radius']+1):
            for z in range(c['center_z']-c['roof_radius'],c['center_z']+c['roof_radius']+1):
                r=math.hypot(x-c['center_x'],z-c['center_z'])
                if r<=c['roof_radius']:
                    y=round(roof_height(r,c))
                    if (x,y,z) not in self.model.blocks:
                        missing.append((x,y,z))
        self.assertEqual([],missing)

    def test_front_entry_is_open_for_people(self):
        c=self.c
        for x in range(c['center_x']-2,c['center_x']+3):
            for y in range(5,9):
                for z in range(c['center_z'],c['center_z']+c['base_radius']+15):
                    self.assertNotIn((x,y,z),self.model.blocks)

    def test_guides_have_the_declared_voxel_clearance(self):
        c=self.c
        self.assertEqual([9,9],self.route['clearance']['inclusive_voxel_span'])
        for x,y,z in self.route['centerline_points']:
            for dx in range(-4,5):
                for dy in range(-3,6):
                    for dz in range(-4,5):
                        self.assertNotIn((x+dx,y+dy,z+dz),self.model.blocks)

    def test_no_generated_rails_or_mod_block_entities(self):
        self.assertTrue(all(b.startswith('minecraft:') for b in self.model.blocks.values()))
        self.assertFalse(any('rail' in b for b in self.model.blocks.values()))

    def test_total_site_and_height_fit_the_design_extent(self):
        n=self.c['site_size']
        self.assertTrue(all(0<=x<n and 0<=z<n and 0<=y<=78 for x,y,z in self.model.blocks))

    def test_user_keepout_survives_later_overlays(self):
        m=Model([([1,1,1],[3,3,3])])
        m.box((0,0,0),(4,4,4),'minecraft:white_concrete','first')
        m.line((0,0,0),(4,4,4),'minecraft:smooth_quartz','later',radius=1)
        self.assertEqual([], [p for p in m.blocks if m.is_protected(*p)])
        self.assertGreater(m.skipped['later'],0)

    def test_invalid_keepout_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'bad.json'
            p.write_text(json.dumps({'areas':[{'min':[1,5,1],'max':[1,2,1]}]}))
            with self.assertRaises(ValueError):read_user_areas(p)

    def test_exported_quadrants_reproduce_full_exterior_without_overlap(self):
        directory=ROOT/'output'/'schem'
        union={}
        for part in ['north_east','north_west','south_east','south_west']:
            data=read_schem(directory/f'exterior_{part}.schem')
            self.assertEqual((0,0,0),data['origin'])
            self.assertFalse(set(union)&set(data['blocks']))
            union.update(data['blocks'])
        complete=read_schem(directory/'tokyo_old_exterior.schem')
        self.assertEqual(complete['blocks'],union)


if __name__=='__main__':unittest.main()
