import type { LinkingOptions } from "@react-navigation/native";
import type { RootParams } from "./screens/navigation";

/**
 * MB-033 deep links in the app's own scheme (the one its sign-in redirect uses): an order, a meal and the cart. The
 * sign-in callback path belongs to the browser session and is never treated as navigation. A link only names a screen;
 * the server decides what the signed-in account may see (another account's order is "not found").
 */
export function linkingFor(redirectUri: string): LinkingOptions<RootParams> {
  const scheme = redirectUri.slice(0, redirectUri.indexOf(":"));
  return {
    prefixes: [`${scheme}://`],
    filter: (url) => !url.startsWith(`${scheme}://auth/`),
    config: {
      screens: {
        Tabs: { screens: { Discover: "", Orders: "orders" } },
        Order: "orders/:orderId",
        Meal: "meals/:mealId",
        Cart: "cart",
      },
    },
  };
}
