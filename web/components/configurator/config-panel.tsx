"use client";

import type { ConfigField } from "@/lib/config-schema";
import { Slider } from "@/components/ui/slider";
import { Toggle } from "@/components/ui/toggle";

interface ConfigPanelProps {
  title: string;
  fields: ConfigField[];
  values: Record<string, number | boolean>;
  onChange: (key: string, value: number | boolean) => void;
}

export function ConfigPanel({
  title,
  fields,
  values,
  onChange,
}: ConfigPanelProps) {
  return (
    <div className="rounded-xl border border-zinc-800 p-5">
      <h3 className="mb-4 text-base font-semibold text-zinc-200">{title}</h3>
      <div className="space-y-5">
        {fields.map((field) =>
          field.type === "boolean" ? (
            <Toggle
              key={field.key}
              label={field.label}
              checked={values[field.key] as boolean}
              tooltip={field.tooltip}
              onChange={(v) => onChange(field.key, v)}
            />
          ) : (
            <Slider
              key={field.key}
              label={field.label}
              value={values[field.key] as number}
              min={field.min!}
              max={field.max!}
              step={field.step!}
              tooltip={field.tooltip}
              onChange={(v) => onChange(field.key, v)}
            />
          ),
        )}
      </div>
    </div>
  );
}
