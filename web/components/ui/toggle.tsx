"use client";

interface ToggleProps {
  label: string;
  checked: boolean;
  tooltip?: string;
  onChange: (checked: boolean) => void;
}

export function Toggle({ label, checked, tooltip, onChange }: ToggleProps) {
  return (
    <label className="flex cursor-pointer items-center justify-between gap-4 py-1">
      <div>
        <span className="text-sm font-medium text-zinc-300">{label}</span>
        {tooltip && (
          <p className="text-xs text-zinc-500">{tooltip}</p>
        )}
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        onClick={() => onChange(!checked)}
        className={`relative h-6 w-11 shrink-0 rounded-full transition-colors ${
          checked ? "bg-emerald-600" : "bg-zinc-700"
        }`}
      >
        <span
          className={`absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white transition-transform ${
            checked ? "translate-x-5" : "translate-x-0"
          }`}
        />
      </button>
    </label>
  );
}
