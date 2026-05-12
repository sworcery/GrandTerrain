"use client";

interface SliderProps {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  tooltip?: string;
  onChange: (value: number) => void;
}

export function Slider({
  label,
  value,
  min,
  max,
  step,
  tooltip,
  onChange,
}: SliderProps) {
  return (
    <label className="block">
      <div className="mb-1 flex items-center justify-between">
        <span className="text-sm font-medium text-zinc-300">{label}</span>
        <span className="text-sm tabular-nums text-zinc-500">{value}</span>
      </div>
      {tooltip && (
        <p className="mb-2 text-xs text-zinc-500">{tooltip}</p>
      )}
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full accent-emerald-500"
      />
      <div className="mt-0.5 flex justify-between text-xs text-zinc-600">
        <span>{min}</span>
        <span>{max}</span>
      </div>
    </label>
  );
}
