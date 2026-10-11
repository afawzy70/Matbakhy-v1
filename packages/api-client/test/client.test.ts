import { describe, expect, it } from "vitest";
import { createMatbakhyClient } from "../src";

describe("typed client", () => {
  it("builds typed requests against the contract paths", async () => {
    const seen: string[] = [];
    const client = createMatbakhyClient({
      baseUrl: "https://api.test.invalid",
      fetch: async (request: Request) => {
        seen.push(`${request.method} ${new URL(request.url).pathname}${new URL(request.url).search}`);
        return new Response(JSON.stringify({ items: [], nextCursor: null, hasMore: false }), {
          status: 200, headers: { "Content-Type": "application/json" },
        });
      },
    });
    const { data } = await client.GET("/api/v1/kitchens/{kitchenId}/slots", {
      params: { path: { kitchenId: "00000000-0000-4000-8000-000000000003" }, query: { limit: 5 } },
    });
    expect(seen).toEqual(["GET /api/v1/kitchens/00000000-0000-4000-8000-000000000003/slots?limit=5"]);
    expect(data?.hasMore).toBe(false);

    // @ts-expect-error unknown paths are rejected at compile time
    void client.GET("/api/v1/not-in-contract");
  });
});
