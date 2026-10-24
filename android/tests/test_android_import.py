# -*- coding: utf-8 -*-
"""Składanie rozklad.json w Javie (Assembler) kontra wersja w C (rz_import.c) i wzorzec w Pythonie.
Uruchom z katalogu głównego repozytorium aplikacji iOS albo ustaw RZ_IOS na jego ścieżkę."""
import json, os, random, shutil, subprocess, sys, tempfile, unittest

HERE = os.path.dirname(os.path.abspath(__file__))
IOS = os.environ.get('RZ_IOS', os.path.join(HERE, '..', '..', '..', 'rozklad-app'))
sys.path.insert(0, os.path.join(IOS, 'tools', 'tests')); sys.path.insert(0, os.path.join(IOS, 'tools'))
import build_data, fake_gtfs, test_build_data as T

CLASSES = os.environ.get('RZ_CLASSES', '/tmp/claude-0/jbuild')
TESTS = os.environ.get('RZ_TESTS', '/tmp/claude-0/jtest')


class A(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.mkdtemp()
        cls.cli = os.path.join(cls.tmp, 'cli')
        subprocess.check_call(['gcc', '-std=c99', '-O1', '-Wall', '-o', cls.cli,
                               os.path.join(IOS, 'tests', 'import_cli.c'), os.path.join(IOS, 'rz_import.c')])
        cls.zip = os.path.join(cls.tmp, 'w.zip')
        fake_gtfs.make(cls.zip, trips_per_route=60)
        cls.site = os.path.join(cls.tmp, 'site')
        cls.v = build_data.build(cls.zip, cls.site, log=lambda *_: None)
        with open(os.path.join(cls.site, 'index.json')) as f:
            cls.idx = json.load(f)
        cls.poles = [(p[0], p[2]) for g in cls.idx['g'] for p in g[5]]

    def java(self, pm, site=None):
        arg = ','.join('%d.%d' % x for x in pm)
        return subprocess.run(['java', '-Dfile.encoding=UTF-8', '-cp', CLASSES + ':' + TESTS, 'AsmCli',
                               site or self.site, self.v, arg], capture_output=True)

    def c(self, pm, site=None):
        arg = ','.join('%d.%d' % x for x in pm)
        return subprocess.run([self.cli, site or self.site, self.v, arg], capture_output=True)

    def test_matches_c_and_reference(self):
        rnd = random.Random(11)
        for trial in range(25):
            sel = rnd.sample(self.poles, rnd.randint(1, len(self.poles)))
            pm = [(n, rnd.choice([m, m, 15, 1, 2, 4, 8, 3]) if trial % 2 else m) for n, m in sel]
            rj = self.java(pm)
            self.assertEqual(rj.returncode, 0, rj.stderr)
            got = json.loads(rj.stdout.decode('utf-8'))
            rc = self.c(pm)
            self.assertEqual(rc.returncode, 0, rc.stderr)
            self.assertEqual(got, json.loads(rc.stdout.decode('ascii')), pm)
            self.assertEqual(got, T.assemble(self.site, self.v, pm), pm)

    def test_garbage_same_verdict(self):
        """Uszkodzone pliki: Java i C zgadzają się co do błędu (kod 5/7) lub przyjęcia; Java nigdy nie wywala się wyjątkiem."""
        rnd = random.Random(5)
        files = []
        for root, _, fs in os.walk(os.path.join(self.site, 'd')):
            files += [os.path.join(root, f) for f in fs]
        agree = 0
        for trial in range(120):
            site = os.path.join(self.tmp, 'mut')
            shutil.rmtree(site, ignore_errors=True)
            shutil.copytree(self.site, site)
            victim = os.path.join(site, os.path.relpath(rnd.choice(files), self.site))
            with open(victim, 'rb') as f:
                data = bytearray(f.read())
            how = trial % 5
            if how == 0 and len(data) > 2:
                data = data[:rnd.randrange(1, len(data))]
            elif how == 1:
                for _ in range(rnd.randint(1, 6)):
                    data[rnd.randrange(len(data))] = rnd.randrange(32, 127)
            elif how == 2:
                i = rnd.randrange(len(data)); data[i:i] = rnd.choice([b'[', b']', b'{', b'"', b',', b'\\', b'9999999999999999999'])
            elif how == 3:
                i = rnd.randrange(len(data)); del data[i:i + rnd.randint(1, 8)]
            else:
                data = bytearray(b'[' * 5000 + b']' * 5000)
            with open(victim, 'wb') as f:
                f.write(data)
            rj = self.java(self.poles, site=site)
            rc = self.c(self.poles, site=site)
            self.assertIn(rj.returncode, (0, 5, 7), (trial, victim, rj.returncode, rj.stderr[-300:]))
            self.assertIn(rc.returncode, (0, 5, 7))
            if rj.returncode == 0 and rc.returncode == 0:
                self.assertEqual(json.loads(rj.stdout.decode('utf-8')), json.loads(rc.stdout.decode('ascii')))
            if rj.returncode == rc.returncode:
                agree += 1
        # różnice dopuszczalne tylko przy niewinnych zmianach (np. zmieniona litera w nazwie)
        self.assertGreater(agree, 100)


if __name__ == '__main__':
    unittest.main()
