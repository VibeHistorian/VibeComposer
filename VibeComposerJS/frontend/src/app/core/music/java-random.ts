const MULTIPLIER = 0x5deece66dn;
const ADDEND = 0xbn;
const MASK = (1n << 48n) - 1n;

/** A framework-independent implementation of java.util.Random's seeded stream. */
export class JavaRandom {
  private seed: bigint;

  constructor(seed: bigint | number) {
    if (typeof seed === 'number' && !Number.isSafeInteger(seed)) {
      throw new RangeError('Numeric seeds must be safe integers; use bigint for 64-bit seeds.');
    }

    this.seed = (BigInt.asIntN(64, BigInt(seed)) ^ MULTIPLIER) & MASK;
  }

  next(bits: number): number {
    if (!Number.isInteger(bits) || bits < 1 || bits > 32) {
      throw new RangeError('bits must be an integer from 1 through 32.');
    }

    this.seed = (this.seed * MULTIPLIER + ADDEND) & MASK;
    return Number(this.seed >> BigInt(48 - bits));
  }

  nextInt(bound?: number): number {
    if (bound === undefined) {
      return this.next(32) | 0;
    }
    if (!Number.isInteger(bound) || bound <= 0 || bound > 0x7fffffff) {
      throw new RangeError('bound must be a positive 32-bit signed integer.');
    }

    if ((bound & -bound) === bound) {
      return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
    }

    let bits: number;
    let value: number;
    do {
      bits = this.next(31);
      value = bits % bound;
    } while (bits - value + (bound - 1) >= 0x80000000);

    return value;
  }

  nextLong(): bigint {
    const high = BigInt.asIntN(32, BigInt(this.next(32)));
    const low = BigInt.asIntN(32, BigInt(this.next(32)));
    return BigInt.asIntN(64, (high << 32n) + low);
  }

  nextBoolean(): boolean {
    return this.next(1) !== 0;
  }

  nextDouble(): number {
    const high = this.next(26);
    const low = this.next(27);
    return (high * 134217728 + low) / 9007199254740992;
  }
}
