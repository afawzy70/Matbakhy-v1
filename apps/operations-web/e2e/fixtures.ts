import { execFileSync } from "node:child_process";
import { crc32, deflateSync } from "node:zlib";

export function env(name: string): string {
  const v = process.env[name];
  if (!v) {
    throw new Error(`${name} is not set; run through scripts/dev.sh e2e`);
  }
  return v;
}

/** Runs SQL against the throwaway stack's database (test setup only; synthetic data). */
export function sql(statement: string): string {
  const prefix = JSON.parse(env("E2E_PSQL")) as string[];
  const [cmd, ...args] = prefix;
  return execFileSync(cmd as string, [...args, "-At", "-v", "ON_ERROR_STOP=1", "-c", statement], { encoding: "utf8" }).trim();
}

/** A synthetic solid-colour PNG (no metadata) large enough for the meal-photo policy. */
export function png(width: number, height: number): Buffer {
  const chunk = (type: string, data: Buffer) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crc]);
  };
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header.writeUInt8(8, 8); // bit depth
  header.writeUInt8(2, 9); // RGB
  const row = Buffer.alloc(1 + width * 3);
  for (let x = 0; x < width; x++) {
    row.writeUInt8(200, 1 + x * 3);
    row.writeUInt8(120, 2 + x * 3);
    row.writeUInt8(60, 3 + x * 3);
  }
  const raw = Buffer.concat(Array.from({ length: height }, () => row));
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", header),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

/** Cairo calendar date `days` from now. */
export function cairoDate(days: number): string {
  const d = new Date(Date.now() + days * 86_400_000);
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Africa/Cairo" }).format(d);
}
