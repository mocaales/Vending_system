import { InputHTMLAttributes } from "react";

// Vnosno polje z oznako; ostale lastnosti (type, required, defaultValue ...) gredo na <input>.
export function Field({
  name,
  label,
  ...input
}: { name: string; label: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <div className="form-group" style={{ margin: 0 }}>
      <label htmlFor={name}>{label}</label>
      <input id={name} name={name} {...input} />
    </div>
  );
}
