import { useEffect, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { publicApi, PublicMachine, GatewayError } from "../api/client";
import { getErrorMessage } from "../api/errors";

// Fix leaflet default icon paths broken by bundlers
delete (L.Icon.Default.prototype as unknown as Record<string, unknown>)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
});

const unavailableIcon = new L.Icon({
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  iconRetinaUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
  className: "marker-unavailable",
});

export default function MapPage() {
  const [machines, setMachines] = useState<PublicMachine[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    publicApi
      .machines()
      .then((res) => setMachines(res.items))
      .catch((err) => {
        const msg =
          err instanceof GatewayError ? getErrorMessage(err.body.code) : "Napaka pri nalaganju.";
        setError(msg);
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <p>Nalagam...</p>;
  if (error) return <p style={{ color: "red" }}>{error}</p>;

  return (
    <div>
      <h1>Prodajni avtomati</h1>
      <MapContainer center={[46.1512, 14.9955]} zoom={8} style={{ height: "70vh", width: "100%" }}>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        {machines.map((m) => (
          <Marker
            key={m.id}
            position={[m.latitude, m.longitude]}
            icon={m.available ? new L.Icon.Default() : unavailableIcon}
          >
            <Popup>
              <strong>{m.name}</strong>
              <br />
              {m.address}
              <br />
              {m.available ? "✓ Razpoložljiv" : "✗ Ni razpoložljiv"}
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}
