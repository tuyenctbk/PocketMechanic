package com.example.ai

import com.example.model.DiagnosticResult
import com.example.model.DiagnosticSeverity
import com.example.model.EquipmentCategory
import com.example.model.SafetyHazardType
import com.example.model.SafetyWarning
import com.example.model.SafetyWarningLevel

/**
 * Curated on-device mechanical fault reference used ONLY as an explicit, clearly labeled
 * offline fallback when the user has no internet, selects Offline Mode, or chooses the
 * zero-cost instant lookup after exhausting daily free AI quota.
 */
object OfflineDiagnosticReference {

    private data class ReferenceEntry(
        val keywords: List<String>,
        val categories: Set<EquipmentCategory>,
        val title: String,
        val primaryCulprit: String,
        val severity: DiagnosticSeverity,
        val summary: String,
        val warnings: List<SafetyWarning>,
        val steps: List<String>,
        val partsTools: List<String>
    )

    private val entries = listOf(
        ReferenceEntry(
            keywords = listOf("p0300", "p0301", "p0302", "p0303", "p0304", "misfire"),
            categories = setOf(EquipmentCategory.VEHICLE, EquipmentCategory.YARD_TOOL),
            title = "Offline Ref: Cylinder Misfire (OBD P030x)",
            primaryCulprit = "Worn spark plug, failing ignition coil, or vacuum/fuel injector fault.",
            severity = DiagnosticSeverity.HIGH,
            summary = "Offline Quick-Reference (no cloud AI): P030x indicates unburned fuel in one or more cylinders. Driving with a flashing Check Engine Light can overheat and damage the catalytic converter.",
            warnings = listOf(
                SafetyWarning(
                    id = "off_misfire_1",
                    title = "Catalytic Converter & Hot Engine Risk",
                    detail = "Do not drive at highway speeds if the Check Engine Light is flashing. Let the engine cool fully before removing coils or spark plugs.",
                    level = SafetyWarningLevel.DANGER,
                    hazardType = SafetyHazardType.THERMAL
                )
            ),
            steps = listOf(
                "Confirm the exact cylinder number from the OBD-II code (e.g., P0301 = Cylinder 1).",
                "Swap the suspect cylinder's ignition coil with a neighboring cylinder and clear codes to test if the misfire moves.",
                "Remove and inspect the spark plug for oil fouling, cracked ceramic, or excessive electrode gap.",
                "Inspect intake vacuum hoses and fuel injector harness connectors for cracks or loose clips."
            ),
            partsTools = listOf("OBD-II scanner", "Spark plug socket & extension", "Torque wrench", "Replacement spark plugs / ignition coil")
        ),
        ReferenceEntry(
            keywords = listOf("belt", "squeal", "chirp", "cold start", "pulley"),
            categories = setOf(EquipmentCategory.VEHICLE, EquipmentCategory.YARD_TOOL),
            title = "Offline Ref: Serpentine Belt or Tensioner Slip",
            primaryCulprit = "Glazed/cracked serpentine belt or weak spring-loaded belt tensioner pulley.",
            severity = DiagnosticSeverity.MEDIUM,
            summary = "Offline Quick-Reference (no cloud AI): High-pitched squealing on cold start or RPM changes typically points to belt slip, glazed ribs, or a dry idler/tensioner bearing.",
            warnings = listOf(
                SafetyWarning(
                    id = "off_belt_1",
                    title = "Rotating Pulley Entanglement Hazard",
                    detail = "Shut off the engine and remove the key before touching belts or pulleys. Keep hands, sleeves, and hair clear while running.",
                    level = SafetyWarningLevel.DANGER,
                    hazardType = SafetyHazardType.MECHANICAL
                )
            ),
            steps = listOf(
                "With the engine OFF, inspect belt ribs for cracks, fraying, glazing, or coolant/oil contamination.",
                "Take a photo of the belt routing diagram before releasing tension.",
                "Release belt tension and spin the idler, tensioner, and alternator pulleys by hand to check for grinding or play.",
                "Replace the belt (and noisy tensioner pulley) if cracks or roughness are found."
            ),
            partsTools = listOf("Serpentine belt", "Breaker bar or tensioner tool", "Flashlight", "Belt tensioner / idler pulley")
        ),
        ReferenceEntry(
            keywords = listOf("knock", "tick", "lifter", "bearing", "grind"),
            categories = setOf(EquipmentCategory.VEHICLE, EquipmentCategory.YARD_TOOL),
            title = "Offline Ref: Rhythmic Engine Knock or Bearing Wear",
            primaryCulprit = "Low engine oil level/pressure, worn valvetrain lifter, or rotating bearing wear.",
            severity = DiagnosticSeverity.CRITICAL,
            summary = "Offline Quick-Reference (no cloud AI): Metallic rhythmic knocking or grinding that accelerates with RPM can indicate oil starvation or internal bearing/valvetrain wear. Verify oil level immediately before restarting.",
            warnings = listOf(
                SafetyWarning(
                    id = "off_knock_1",
                    title = "Stop Engine Immediately If Oil Pressure Is Low",
                    detail = "Running an engine with a deep bottom-end rod knock or low oil warning can cause catastrophic engine seizure.",
                    level = SafetyWarningLevel.DANGER,
                    hazardType = SafetyHazardType.MECHANICAL
                )
            ),
            steps = listOf(
                "Park on level ground, turn off the engine, wait 5 minutes, and check the oil dipstick for level and metallic sheen.",
                "Confirm the engine oil viscosity matches the filler cap and check for active oil leaks underneath.",
                "Isolate whether the sound comes from upper valvetrain (light tick) vs. lower block (deep knock) or an accessory pulley.",
                "If oil is full and deep knocking persists, tow to a mechanic rather than driving."
            ),
            partsTools = listOf("Engine oil (manufacturer spec)", "Oil filter", "Mechanic's stethoscope", "Flashlight")
        ),
        ReferenceEntry(
            keywords = listOf("water hammer", "bang", "pipe rattle", "pipes", "shut off"),
            categories = setOf(EquipmentCategory.PLUMBING),
            title = "Offline Ref: Plumbing Water Hammer / Loose Pipe",
            primaryCulprit = "Fast-closing solenoid valve shockwave, high water pressure (>80 PSI), or unsecured pipe straps.",
            severity = DiagnosticSeverity.MEDIUM,
            summary = "Offline Quick-Reference (no cloud AI): Loud banging when a washer, dishwasher, or faucet shuts off is hydraulic shock (water hammer), worsened by high static pressure or loose pipe clamps.",
            warnings = listOf(
                SafetyWarning(
                    id = "off_hammer_1",
                    title = "Shut Off Main Water Before Opening Lines",
                    detail = "Close the main shutoff valve and open the lowest faucet to relieve water pressure before installing arrestors or tightening fittings.",
                    level = SafetyWarningLevel.WARNING,
                    hazardType = SafetyHazardType.PRESSURE
                )
            ),
            steps = listOf(
                "Check home static water pressure at an exterior hose bib with a pressure gauge (safe range: 50–75 PSI).",
                "Drain and recharge plumbing air chambers by shutting off the main supply, opening highest and lowest taps, then refilling slowly.",
                "Install a brass water hammer arrestor tee on washing machine and dishwasher supply valves.",
                "Inspect accessible basement or under-sink piping and add cushioned pipe clamps where lines vibrate."
            ),
            partsTools = listOf("Water pressure gauge", "Water hammer arrestor (3/4\" hose or 1/2\" tee)", "Cushioned pipe clamps", "Adjustable wrench")
        ),
        ReferenceEntry(
            keywords = listOf("p-trap", "drip", "leak", "sink", "gurgle", "drain"),
            categories = setOf(EquipmentCategory.PLUMBING),
            title = "Offline Ref: Under-Sink P-Trap Leak or Vent Gurgle",
            primaryCulprit = "Misaligned slip-joint washer, cracked P-trap nut, or partial drain/vent obstruction.",
            severity = DiagnosticSeverity.MEDIUM,
            summary = "Offline Quick-Reference (no cloud AI): Slow under-sink drips usually stem from compressed bevel washers or cross-threaded slip nuts; gurgling indicates a partial clog or obstructed air admittance valve (AAV).",
            warnings = listOf(
                SafetyWarning(
                    id = "off_ptrap_1",
                    title = "Avoid Chemical Drain Cleaners Before Disassembly",
                    detail = "Never open a trap containing caustic liquid drain opener—wear nitrile gloves and eye protection and keep a catch bucket underneath.",
                    level = SafetyWarningLevel.WARNING,
                    hazardType = SafetyHazardType.CHEMICAL
                )
            ),
            steps = listOf(
                "Place a bucket and towel beneath the P-trap, dry every joint thoroughly, and run water while watching with a flashlight to pinpoint the exact joint.",
                "Loosen the slip nuts by hand, clear hair/grease buildup from the U-bend, and inspect bevel washers for cracks or flat spots.",
                "Reassemble with fresh slip-joint washers oriented with the tapered bevel facing the fitting seat, tightening snugly by hand (plus 1/4 turn max).",
                "Fill the basin and release a full sink of water to confirm a dry seal and smooth draining."
            ),
            partsTools = listOf("1-1/2\" slip-joint bevel washers", "Channel-lock pliers", "Catch bucket & towels", "Nitrile gloves")
        ),
        ReferenceEntry(
            keywords = listOf("oe", "e24", "f21", "5e", "drain code", "washer", "dishwasher"),
            categories = setOf(EquipmentCategory.APPLIANCE),
            title = "Offline Ref: Appliance Drain Error (OE / E24 / F21)",
            primaryCulprit = "Clogged coin trap / drain pump filter, kinked drain hose, or failing drain pump impeller.",
            severity = DiagnosticSeverity.MEDIUM,
            summary = "Offline Quick-Reference (no cloud AI): Washer and dishwasher drain codes trigger when water cannot evacuate within the cycle timeout—most commonly blocked debris filters or kinked hoses.",
            warnings = listOf(
                SafetyWarning(
                    id = "off_drain_1",
                    title = "Unplug Appliance Before Accessing Pump",
                    detail = "Disconnect AC power from the wall outlet and keep towels/shallow pan ready for standing water before opening the drain filter.",
                    level = SafetyWarningLevel.DANGER,
                    hazardType = SafetyHazardType.ELECTRICAL
                )
            ),
            steps = listOf(
                "Unplug the appliance and check behind the unit for a crushed or kinked corrugated drain hose.",
                "Open the lower front access panel (on front-load washers) and drain residual water slowly using the small drain tube.",
                "Unscrew the coin trap filter, remove coins/lint/hairpins, and spin the pump impeller with a finger to ensure free rotation.",
                "Verify the sink/standpipe connection or garbage disposal knockout plug is clear, then run a short Rinse & Spin test."
            ),
            partsTools = listOf("Shallow drain pan & towels", "Flashlight", "Needle-nose pliers", "Nut driver / Phillips screwdriver")
        ),
        ReferenceEntry(
            keywords = listOf("ac", "condenser", "buzz", "hvac", "fan not spinning", "capacitor", "relay"),
            categories = setOf(EquipmentCategory.HVAC, EquipmentCategory.APPLIANCE, EquipmentCategory.OTHER),
            title = "Offline Ref: HVAC Condenser Buzz / Start Capacitor Fault",
            primaryCulprit = "Failed dual-run start capacitor, pitted contactor relay, or seized condenser fan motor.",
            severity = DiagnosticSeverity.HIGH,
            summary = "Offline Quick-Reference (no cloud AI): Loud humming/buzzing from an outdoor AC condenser while the fan refuses to start frequently points to a swollen/failed run capacitor or stuck contactor.",
            warnings = listOf(
                SafetyWarning(
                    id = "off_hvac_1",
                    title = "High-Voltage 240V & Stored Capacitor Charge",
                    detail = "Pull the outdoor disconnect switch AND turn off the breaker. Capacitors hold lethal voltage even with power off—never touch terminals without verifying 0V and safely discharging.",
                    level = SafetyWarningLevel.DANGER,
                    hazardType = SafetyHazardType.ELECTRICAL
                )
            ),
            steps = listOf(
                "Turn the thermostat to OFF immediately so the compressor does not overheat trying to start.",
                "Verify the outdoor disconnect box and main 240V breaker are not tripped.",
                "With power strictly disconnected and verified dead via multimeter, visually inspect the capacitor top for bulging or leaked oil.",
                "If uncomfortable testing 240V components or discharging capacitors, call a licensed HVAC technician."
            ),
            partsTools = listOf("Digital multimeter (with µF capacitance test)", "Insulated screwdrivers", "Replacement dual-run capacitor (exact µF & voltage match)")
        ),
        ReferenceEntry(
            keywords = listOf("p0171", "p0420", "p0442", "evap", "gas cap", "check engine"),
            categories = setOf(EquipmentCategory.VEHICLE),
            title = "Offline Ref: Emissions / Vacuum OBD-II Fault",
            primaryCulprit = "Loose/cracked gas cap seal (P0442), intake vacuum leak or dirty MAF sensor (P0171), or O2/catalyst wear (P0420).",
            severity = DiagnosticSeverity.MEDIUM,
            summary = "Offline Quick-Reference (no cloud AI): Common emissions OBD-II codes often start with simple seal or airflow issues before requiring expensive exhaust parts.",
            warnings = listOf(
                SafetyWarning(
                    id = "off_obd_1",
                    title = "Work in Ventilated Area Away From Fuel Vapors",
                    detail = "Keep sparks and open flames away when inspecting fuel caps, EVAP purge hoses, or cleaning intake sensors.",
                    level = SafetyWarningLevel.WARNING,
                    hazardType = SafetyHazardType.CHEMICAL
                )
            ),
            steps = listOf(
                "For EVAP codes (P0442/P0455), inspect the gas cap rubber O-ring for cracks and tighten until it clicks firmly.",
                "For lean codes (P0171), inspect rubber intake boots and PCV hoses for splits or audible vacuum hissing.",
                "Clean the Mass Airflow (MAF) sensor using dedicated MAF sensor cleaner spray (never brake cleaner).",
                "Clear codes and monitor readiness over 2–3 drive cycles; connect BYOK or Free AI later for model-specific manual specs."
            ),
            partsTools = listOf("OBD-II scanner", "MAF sensor cleaner", "Replacement gas cap", "Flashlight")
        )
    )

    fun lookup(
        category: EquipmentCategory,
        equipmentName: String,
        symptomNotes: String,
        hasAudio: Boolean = false,
        hasImage: Boolean = false
    ): DiagnosticResult {
        val haystack = "$equipmentName $symptomNotes".lowercase()

        val scored = entries.map { entry ->
            var score = 0
            entry.keywords.forEach { kw ->
                if (haystack.contains(kw)) score += 3
            }
            if (category in entry.categories) score += 1
            entry to score
        }.maxByOrNull { it.second }

        val best = scored?.takeIf { it.second >= 3 }?.first
        if (best != null) {
            return DiagnosticResult(
                title = best.title,
                primaryCulprit = best.primaryCulprit,
                confidencePercent = 65,
                severity = best.severity,
                summary = best.summary,
                safetyWarnings = best.warnings,
                diySteps = best.steps,
                partsTools = best.partsTools,
                sources = emptyList()
            )
        }

        val mediaHint = buildList {
            if (hasAudio) add("captured audio")
            if (hasImage) add("captured photo")
        }.joinToString(" and ")

        val mediaNote = if (mediaHint.isNotBlank()) {
            " Your $mediaHint requires cloud AI (Free Daily Tier or Personal BYOK Gemini Key) for deep waveform/image inspection."
        } else {
            " Add specific OBD-II/error codes (e.g., P0301, OE, E24) or enable your free BYOK Gemini key for full AI manual grounding."
        }

        return DiagnosticResult(
            title = "Offline Ref: ${category.displayName} Baseline Inspection",
            primaryCulprit = "No exact offline fault match for \"${symptomNotes.ifBlank { equipmentName.ifBlank { category.displayName } }.take(60)}\".",
            confidencePercent = 45,
            severity = DiagnosticSeverity.MEDIUM,
            summary = "Offline Quick-Reference (no cloud AI): Follow this baseline safety & isolation checklist for ${category.displayName.lowercase()} issues.$mediaNote",
            safetyWarnings = listOf(
                SafetyWarning(
                    id = "off_gen_1",
                    title = "Isolate Power, Water, or Fuel First",
                    detail = "Disconnect electrical power, shut off water/gas supply, or chock wheels and allow hot surfaces to cool before inspecting.",
                    level = SafetyWarningLevel.WARNING,
                    hazardType = SafetyHazardType.GENERAL
                )
            ),
            diySteps = listOf(
                "Record any flashing LED blink counts, display error codes, or OBD-II fault codes.",
                "Inspect the exterior with your inspection flashlight for loose connectors, fluid drips, frayed belts, or burnt wiring.",
                "Note whether the symptom occurs at startup, under load, or continuously.",
                "When back online—or with your free personal Gemini BYOK key—re-run for full audio/vision AI diagnosis."
            ),
            partsTools = listOf("Inspection flashlight", "Basic socket & screwdriver set", "Multimeter or OBD-II reader"),
            sources = emptyList()
        )
    }
}
