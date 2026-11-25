// Gateway error code → Slovenian message
const ERROR_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: "Napačen e-naslov ali geslo.",
  RATE_LIMITED: "Preveč poskusov prijave. Počakajte in poskusite znova.",
  UNAUTHENTICATED: "Seja je potekla. Prijavite se znova.",
  FORBIDDEN: "Nimate dovoljenja za to dejanje.",
  NOT_FOUND: "Zahtevan vir ne obstaja.",
  VALIDATION_ERROR: "Vneseni podatki niso veljavni.",
  OPERATOR_EMAIL_TAKEN: "Ta e-naslov je že v uporabi.",
  OPERATOR_NOT_FOUND: "Operater ni najden.",
  OPERATOR_IS_SELF: "Te operacije ne morete izvesti nad lastnim računom.",
  OPERATOR_LAST_ACTIVE: "Ne morete deaktivirati zadnjega aktivnega operaterja.",
  MACHINE_NOT_FOUND: "Avtomat ni najden.",
  MACHINE_RETIRED: "Avtomat je umaknjen.",
  MACHINE_HAS_STOCK: "Avtomat ima zalogo.",
  DEPENDENCY_UNAVAILABLE: "Storitev trenutno ni dosegljiva.",
  UPSTREAM_TIMEOUT: "Storitev se ne odziva.",
  INTERNAL_ERROR: "Prišlo je do napake na strežniku.",
};

export const getErrorMessage = (code: string): string => ERROR_MESSAGES[code] ?? `Napaka: ${code}`;
