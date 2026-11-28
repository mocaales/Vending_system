// Lažni gateway za teste komponent: nadomesti fetch in odgovarja po tabeli
// "METODA /pot" -> [status, telo]; vrne seznam poslanih zahtev.
// Ključ s poizvedbo ("GET /pot?status=X") ima prednost pred ključem brez nje.
type Answer = [number, unknown];
export type Routes = Record<string, Answer>;

export interface SentRequest {
  method: string;
  path: string; // s poizvedbo, npr. /api/v1/products?status=ACTIVE
  body: unknown;
}

export function fakeGateway(routes: Routes): SentRequest[] {
  const requests: SentRequest[] = [];
  const fetch = async (url: string, init: RequestInit = {}) => {
    const { pathname, search } = new URL(url, "http://ui");
    const method = init.method ?? "GET";
    requests.push({
      method,
      path: pathname + search,
      body: init.body ? JSON.parse(String(init.body)) : undefined,
    });
    const answer = routes[`${method} ${pathname}${search}`] ?? routes[`${method} ${pathname}`];
    if (!answer) throw new Error(`fakeGateway: no route for ${method} ${pathname}`);
    const [status, body] = answer;
    return new Response(status === 204 ? null : JSON.stringify(body), { status });
  };
  vi.stubGlobal("fetch", vi.fn(fetch));
  return requests;
}

export const page = <T>(items: T[]) => ({ items, total: items.length, limit: 200, offset: 0 });

export const gatewayError = (code: string) => ({ code, message: code, traceId: "t1" });
