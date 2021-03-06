/**
 * @license
 * Copyright (c) 2018 Google Inc. All rights reserved.
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 * Code distributed by Google as part of this project is also
 * subject to an additional IP rights grant found at
 * http://polymer.github.io/PATENTS.txt
 */
import {assert} from '../../../platform/chai-web.js';
import {Arc} from '../../../runtime/arc.js';
import {Loader} from '../../../runtime/loader.js';
import {Manifest} from '../../../runtime/manifest.js';
import {Modality} from '../../../runtime/modality.js';
import {FakeSlotComposer} from '../../../runtime/testing/fake-slot-composer.js';
import {ConvertConstraintsToConnections} from '../../strategies/convert-constraints-to-connections.js';
import {InstanceEndPoint} from '../../../runtime/recipe/connection-constraint.js';
import {ArcId} from '../../../runtime/id.js';

import {Flags} from '../../../runtime/flags.js';

describe('ConvertConstraintsToConnections', () => {
  const newArc = (manifest: Manifest) => {
    return new Arc({
      id: ArcId.newForTest('test-plan-arc'),
      slotComposer: new FakeSlotComposer(),
      context: manifest,
      loader: new Loader()
    });
  };

  it('SLANDLES SYNTAX fills out an empty constraint', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        inout S {} b
      particle C
        inout S {} d

      recipe
        A.b: out C.d`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  create as handle0 // S {}
  A as particle0
    b: inout handle0
  C as particle1
    d: inout handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('fills out an empty constraint', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        inout S {} b
      particle C
        inout S {} d

      recipe
        A.b -> C.d`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  create as handle0 // S {}
  A as particle0
    b <-> handle0
  C as particle1
    d <-> handle0`);
  }));

  it('SLANDLES SYNTAX does not cause an input only handle to be created', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        in S b
      particle C
        in S d

      recipe
        A.b: out C.d`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.isEmpty(results);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('does not cause an input only handle to be created', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        in S b
      particle C
        in S d

      recipe
        A.b -> C.d`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.isEmpty(results);
  }));

  it('can resolve input only handle connection with a mapped handle', async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        in S b
      particle C
        in S d

      recipe
        map as handle0
        A.b = C.d`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
  });

  it('SLANDLES SYNTAX can create handle for input and output handle', Flags.withPostSlandlesSyntax(async () => {
    const parseManifest = async (constraint1, constraint2) => await Manifest.parse(`
      schema S
      particle A
        in S b
      particle C
        in S d
      particle E
        out S f

      recipe
        ${constraint1}
        ${constraint2}`);
    const verify = async (constraint1, constraint2) => {
      const manifest = await parseManifest(constraint1, constraint2);
      const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
      const cctc = new ConvertConstraintsToConnections(newArc(manifest));
      const results = await cctc.generateFrom(generated);
      assert.lengthOf(results, 1, `Failed to resolve ${constraint1} & ${constraint2}`);
    };
    // Test for all possible combination of connection constraints with 3 particles.
    const constraints = [['A.b: any C.d', 'C.d: any A.b'], ['A.b: out E.f', 'E.f: in A.b'], ['C.d: out E.f', 'E.f: in C.d']];
    for (let i = 0; i < constraints.length; ++i) {
      for (let j = 0; j < constraints.length; ++j) {
        if (i === j) continue;
        for (let ii = 0; ii <= 1; ++ii) {
          for (let jj = 0; jj <= 1; ++jj) {
            await verify(constraints[i][ii], constraints[j][jj]);
          }
        }
      }
    }
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('can create handle for input and output handle', Flags.withPreSlandlesSyntax(async () => {
    const parseManifest = async (constraint1, constraint2) => await Manifest.parse(`
      schema S
      particle A
        in S b
      particle C
        in S d
      particle E
        out S f

      recipe
        ${constraint1}
        ${constraint2}`);
    const verify = async (constraint1, constraint2) => {
      const manifest = await parseManifest(constraint1, constraint2);
      const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
      const cctc = new ConvertConstraintsToConnections(newArc(manifest));
      const results = await cctc.generateFrom(generated);
      assert.lengthOf(results, 1, `Failed to resolve ${constraint1} & ${constraint2}`);
    };
    // Test for all possible combination of connection constraints with 3 particles.
    const constraints = [['A.b = C.d', 'C.d = A.b'], ['A.b -> E.f', 'E.f <- A.b'], ['C.d -> E.f', 'E.f <- C.d']];
    for (let i = 0; i < constraints.length; ++i) {
      for (let j = 0; j < constraints.length; ++j) {
        if (i === j) continue;
        for (let ii = 0; ii <= 1; ++ii) {
          for (let jj = 0; jj <= 1; ++jj) {
            await verify(constraints[i][ii], constraints[j][jj]);
          }
        }
      }
    }
  }));

  it('SLANDLES SYNTAX fills out a constraint, reusing a single particle', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b: out C.d
        C`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  create as handle0 // S {}
  A as particle0
    b: inout handle0
  C as particle1
    d: inout handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('fills out a constraint, reusing a single particle', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b -> C.d
        C`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  create as handle0 // S {}
  A as particle0
    b <-> handle0
  C as particle1
    d <-> handle0`);
  }));

  it('SLANDLES SYNTAX fills out a constraint, reusing a single particle (2)', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b: out C.d
        A`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  create as handle0 // S {}
  A as particle0
    b: inout handle0
  C as particle1
    d: inout handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('fills out a constraint, reusing a single particle (2)', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b -> C.d
        A`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  create as handle0 // S {}
  A as particle0
    b <-> handle0
  C as particle1
    d <-> handle0`);
  }));

  it('SLANDLES SYNTAX fills out a constraint, reusing two particles', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b: out C.d
        C
        A`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  create as handle0 // S {}
  A as particle0
    b: inout handle0
  C as particle1
    d: inout handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('fills out a constraint, reusing two particles', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b -> C.d
        C
        A`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  create as handle0 // S {}
  A as particle0
    b <-> handle0
  C as particle1
    d <-> handle0`);
  }));

  it('SLANDLES SYNTAX fills out a constraint, reusing two particles and a handle', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b: out C.d
        use as handle1
        C
          d: inout handle1
        A`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  use as handle0 // S {}
  A as particle0
    b: inout handle0
  C as particle1
    d: inout handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('fills out a constraint, reusing two particles and a handle', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b -> C.d
        use as handle1
        C
          d <-> handle1
        A`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  use as handle0 // S {}
  A as particle0
    b <-> handle0
  C as particle1
    d <-> handle0`);
  }));

  it('SLANDLES SYNTAX fills out a constraint, reusing two particles and a handle (2)', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b: out C.d
        use as handle1
        C
        A
          b: any handle1`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  use as handle0 // S {}
  A as particle0
    b: inout handle0
  C as particle1
    d: inout handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('fills out a constraint, reusing two particles and a handle (2)', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b -> C.d
        use as handle1
        C
        A
          b = handle1`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(),
`recipe
  use as handle0 // S {}
  A as particle0
    b <-> handle0
  C as particle1
    d <-> handle0`);
  }));

  it('SLANDLES SYNTAX removes an already fulfilled constraint', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b: out C.d
        use as handle1
        C
          d: inout handle1
        A
          b: inout handle1`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(), `recipe
  use as handle0 // S {}
  A as particle0
    b: inout handle0
  C as particle1
    d: inout handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('removes an already fulfilled constraint', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A
        inout S b
      particle C
        inout S d

      recipe
        A.b -> C.d
        use as handle1
        C
          d <-> handle1
        A
          b <-> handle1`);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const {result, score} = results[0];
    assert.deepEqual(result.toString(), `recipe
  use as handle0 // S {}
  A as particle0
    b <-> handle0
  C as particle1
    d <-> handle0`);
  }));

  it('SLANDLES SYNTAX verifies modality', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A in 'A.js'
        out S b
        modality vr
        consume root
      particle C in 'C.js'
        in S d
        modality vr
        consume root
      particle E in 'E.js'
        in S f
        consume root

      recipe
        A.b: out C.d
      recipe
        A.b: out E.f
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}, {result: manifest.recipes[1], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(new Arc({
      id: ArcId.newForTest('test-plan-arc'),
      slotComposer: new FakeSlotComposer({modalityName: Modality.Name.Vr}),
      context: manifest,
      loader: new Loader()
    }));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.particles.map(p => p.name), ['A', 'C']);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('verifies modality', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      schema S
      particle A in 'A.js'
        out S b
        modality vr
        consume root
      particle C in 'C.js'
        in S d
        modality vr
        consume root
      particle E in 'E.js'
        in S f
        consume root

      recipe
        A.b -> C.d
      recipe
        A.b -> E.f
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}, {result: manifest.recipes[1], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(new Arc({
      id: ArcId.newForTest('test-plan-arc'),
      slotComposer: new FakeSlotComposer({modalityName: Modality.Name.Vr}),
      context: manifest,
      loader: new Loader()
    }));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.particles.map(p => p.name), ['A', 'C']);
  }));

  it('SLANDLES SYNTAX connects to handles', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        out S {} o
      particle B
        in S {} i
      recipe
        ? as h
        A.o: out h
        h: out B.i
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? as handle0 // S {}
  A as particle0
    o: out handle0
  B as particle1
    i: in handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('connects to handles', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        out S {} o
      particle B
        in S {} i
      recipe
        ? as h
        A.o -> h
        h -> B.i
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? as handle0 // S {}
  A as particle0
    o -> handle0
  B as particle1
    i <- handle0`);
  }));

  it('SLANDLES SYNTAX connects existing particles to handles', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        out S {} o
      particle B
        in S {} i
      recipe
        ? as h
        A.o: out h
        h: out B.i
        A
        B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? as handle0 // S {}
  A as particle0
    o: out handle0
  B as particle1
    i: in handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('connects existing particles to handles', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        out S {} o
      particle B
        in S {} i
      recipe
        ? as h
        A.o -> h
        h -> B.i
        A
        B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? as handle0 // S {}
  A as particle0
    o -> handle0
  B as particle1
    i <- handle0`);
  }));

  it(`SLANDLES SYNTAX doesn't attempt to duplicate existing handles to particles`, Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        out S {} o
      particle B
        in S {} i
      recipe
        ? as h
        A.o -> h
        h -> B.i
        A
          o -> h
        B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? as handle0 // S {}
  A as particle0
    o: out handle0
  B as particle1
    i: in handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it(`doesn't attempt to duplicate existing handles to particles`, Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        out S {} o
      particle B
        in S {} i
      recipe
        ? as h
        A.o -> h
        h -> B.i
        A
          o -> h
        B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? as handle0 // S {}
  A as particle0
    o -> handle0
  B as particle1
    i <- handle0`);
  }));

  it(`SLANDLES SYNTAX duplicates particles to get handle connections right`, Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        out S {} o
      particle B
        in S {} i
      recipe
        ? as h
        ? as j
        A.o: out h
        h: out B.i
        A
          o: out j
        B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? as handle0 // ~
  ? as handle1 // S {}
  A as particle0
    o: out handle0
  A as particle1
    o: out handle1
  B as particle2
    i: in handle1`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it(`duplicates particles to get handle connections right`, Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
      particle A
        out S {} o
      particle B
        in S {} i
      recipe
        ? as h
        ? as j
        A.o -> h
        h -> B.i
        A
          o -> j
        B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? as handle0 // ~
  ? as handle1 // S {}
  A as particle0
    o -> handle0
  A as particle1
    o -> handle1
  B as particle2
    i <- handle1`);
  }));

  it('SLANDLES SYNTAX connects to tags', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
    particle B
      out S {} i
    recipe
      ? #hashtag
      A.o: out #hashtag
      #trashbag: in B.i
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? #hashtag as handle0 // ~
  create #trashbag as handle1 // ~
  A as particle0
    o: out handle0
  B as particle1
    i: out handle1`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('connects to tags', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
    particle B
      out S {} i
    recipe
      ? #hashtag
      A.o -> #hashtag
      #trashbag <- B.i
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? #hashtag as handle0 // ~
  create #trashbag as handle1 // ~
  A as particle0
    o -> handle0
  B as particle1
    i -> handle1`);
  }));

  it('SLANDLE SYNTAX connects existing particles to tags', Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
    particle B
      out S {} i
    recipe
      ? #hashtag
      A.o: out #hashtag
      #trashbag: in B.i
      A
      B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? #hashtag as handle0 // ~
  create #trashbag as handle1 // ~
  A as particle0
    o: out handle0
  B as particle1
    i: out handle1`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it('connects existing particles to tags', Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
    particle B
      out S {} i
    recipe
      ? #hashtag
      A.o -> #hashtag
      #trashbag <- B.i
      A
      B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? #hashtag as handle0 // ~
  create #trashbag as handle1 // ~
  A as particle0
    o -> handle0
  B as particle1
    i -> handle1`);
  }));

  it(`SLANDLES SYNTAX doesn't attempt to duplicate existing connections to tags`, Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
    particle B
      out S {} i
    recipe
      ? #hashtag as handle0
      A.o: out #hashtag
      #trashbag: in B.i
      A
        o: out handle0
      B
        i: out handle0
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? #hashtag as handle0 // ~
  A as particle0
    o: out handle0
  B as particle1
    i: out handle0`);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it(`doesn't attempt to duplicate existing connections to tags`, Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
    particle B
      out S {} i
    recipe
      ? #hashtag as handle0
      A.o -> #hashtag
      #trashbag <- B.i
      A
        o -> handle0
      B
        i -> handle0
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    assert.deepEqual(results[0].result.toString(), `recipe
  ? #hashtag as handle0 // ~
  A as particle0
    o -> handle0
  B as particle1
    i -> handle0`);
  }));

  it(`SLANDLES SYNTAX connects particles together when there's only one possible connection`, Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
    particle B
      in S {} i
    recipe
      A: out B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const recipe = results[0].result;
    assert.deepEqual(recipe.particles.map(p => p.name), ['A', 'B']);
    assert.lengthOf(recipe.obligations, 1);
    assert.strictEqual((recipe.obligations[0].from as InstanceEndPoint).instance, recipe.particles[0]);
    assert.strictEqual((recipe.obligations[0].to as InstanceEndPoint).instance, recipe.particles[1]);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it(`connects particles together when there's only one possible connection`, Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
    particle B
      in S {} i
    recipe
      A -> B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const recipe = results[0].result;
    assert.deepEqual(recipe.particles.map(p => p.name), ['A', 'B']);
    assert.lengthOf(recipe.obligations, 1);
    assert.strictEqual((recipe.obligations[0].from as InstanceEndPoint).instance, recipe.particles[0]);
    assert.strictEqual((recipe.obligations[0].to as InstanceEndPoint).instance, recipe.particles[1]);
  }));

  it(`SLANDLES SYNTAX connects particles together when there's extra things that can't connect`, Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
      in S {} i
    particle B
      in S {} i
      in T {} i2
    recipe
      A: out B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const recipe = results[0].result;
    assert.deepEqual(recipe.particles.map(p => p.name), ['A', 'B']);
    assert.lengthOf(recipe.obligations, 1);
    assert.strictEqual((recipe.obligations[0].from as InstanceEndPoint).instance, recipe.particles[0]);
    assert.strictEqual((recipe.obligations[0].to as InstanceEndPoint).instance, recipe.particles[1]);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it(`connects particles together when there's extra things that can't connect`, Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
      in S {} i
    particle B
      in S {} i
      in T {} i2
    recipe
      A -> B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const recipe = results[0].result;
    assert.deepEqual(recipe.particles.map(p => p.name), ['A', 'B']);
    assert.lengthOf(recipe.obligations, 1);
    assert.strictEqual((recipe.obligations[0].from as InstanceEndPoint).instance, recipe.particles[0]);
    assert.strictEqual((recipe.obligations[0].to as InstanceEndPoint).instance, recipe.particles[1]);
  }));

  it(`SLANDLES SYNTAX connects particles together with multiple connections`, Flags.withPostSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
      in T {} i
    particle B
      in S {} i
      out T {} o
    recipe
      A: inout B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const recipe = results[0].result;
    assert.deepEqual(recipe.particles.map(p => p.name), ['A', 'B']);
    assert.lengthOf(recipe.obligations, 1);
    assert.strictEqual((recipe.obligations[0].from as InstanceEndPoint).instance, recipe.particles[0]);
    assert.strictEqual((recipe.obligations[0].to as InstanceEndPoint).instance, recipe.particles[1]);
  }));

  // TODO(jopra): Remove once slandles unification syntax is implemented.
  it(`connects particles together with multiple connections`, Flags.withPreSlandlesSyntax(async () => {
    const manifest = await Manifest.parse(`
    particle A
      out S {} o
      in T {} i
    particle B
      in S {} i
      out T {} o
    recipe
      A <-> B
    `);
    const generated = [{result: manifest.recipes[0], score: 1, derivation: [], hash: '0', valid: true}];
    const cctc = new ConvertConstraintsToConnections(newArc(manifest));
    const results = await cctc.generateFrom(generated);
    assert.lengthOf(results, 1);
    const recipe = results[0].result;
    assert.deepEqual(recipe.particles.map(p => p.name), ['A', 'B']);
    assert.lengthOf(recipe.obligations, 1);
    assert.strictEqual((recipe.obligations[0].from as InstanceEndPoint).instance, recipe.particles[0]);
    assert.strictEqual((recipe.obligations[0].to as InstanceEndPoint).instance, recipe.particles[1]);
  }));
});
