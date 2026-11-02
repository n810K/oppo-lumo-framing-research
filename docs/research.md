# LUMO Framing on a Find X9 Pro

Tested November 1–2, 2026, on a Find X9 Pro (CPH2791, ColorOS 17). The original Camera app inspected was version 7.006.125.

## What I wanted to enable

[LUMO Framing](https://www.oppo.com/en/articles/find-x10-series/best-phone-natural-image-processing-detail/) is OPPO's name for the feature that looks at the viewfinder and suggests how to frame a photo. I wanted it on my Find X9 Pro, with the original camera's image quality and automatic zoom.

The test build called it **Live guide**, which is why that name appears in the earlier notes and source files. This article uses LUMO Framing for the feature and keeps Live guide where it refers to that button.

I got the framing suggestions, positioning guide and automatic zoom working without root. The modified app also saved photos, but the quality and colours were worse, the view could jump between lenses, and taking a second photo wasn't reliable. I stopped using it.

## What was already in the app

The X9 Camera app contained code for AI composition, along with two settings that controlled whether it was available. Neither setting appeared in the phone's shipped configuration, so the code treated both as off.

That didn't mean the full feature was ready to use. It also needed compatible model files and the software that runs them. Forcing the settings on was enough to make progress with the interface, but it wasn't enough to make the feature work.

I could inspect the shipped configuration, but not the original Camera's private settings. So this establishes the defaults in the files I read, rather than every value the original app might load later.

## Why I used a separate Camera app

Android checks who signed an app before accepting an update. Rebuilding OPPO's APK with my own changes gives it a different signature, so I couldn't install that modified copy over the original Camera through a normal update.

An unmodified, OPPO-signed donor Camera did install earlier in the investigation. It opened normally but didn't show the framing button on this phone. Its successful installation didn't make a locally modified version a valid update.

Shizuku didn't provide access to the protected settings or OPPO-only services I checked. Changing the signature an app reports to itself wouldn't change the signature Android or another service sees either.

So I installed a separate copy labelled Camera, leaving the original app installed.

## Getting the framing suggestions to work

The first combination of model files and software failed. The model expected a different version of the software that runs it, so I looked for a compatible pair in the downloaded X10 software.

The successful files came from the Find X10 system download (CPH2911). A separate test confirmed that this combination could run on the X9, and I then connected it to the modified OPPO Camera. The model files stayed in the test app's own storage. No protected system files needed to be changed.

The viewfinder needed repairs too. Preview results were being sent to the wrong Camera client, which left the view black or stalled. Keeping the client that had registered to receive those results restored the preview. Disabling HDR preview in the copy removed severe colour corruption; the HDR preview itself was never fixed.

After those changes, the app analysed the live view, suggested a crop and displayed the dot-and-ring guide showing where to point the phone. It also applied the zoom. In one test it requested about 2.5×, and the camera's own reported zoom matched. This was checked separately from the number shown on the screen.

Successful requests took roughly two to five seconds in the recorded tests. Those timings came from individual tests, not a performance comparison with an X10.

## Taking and saving photos

The original photo-processing code received images, but it was still waiting for additional camera data that never arrived. It didn't produce a completed photo. Giving the app another storage permission wouldn't supply that missing data.

I added a separate capture method using Android's standard camera interface. It took a JPEG, saved it to the camera roll and returned to the OPPO viewfinder. Saved files were checked directly: one measured 4096×3072, and a portrait photo measured 3072×4096. They were full-resolution photos, not enlarged thumbnails.

This method bypassed OPPO's original processing, though. The difference in quality and colour was noticeable, and it didn't meet the reason I wanted to keep using the original Camera app in the first place. I didn't run a controlled image-quality comparison, so I can't attribute each difference to a specific processing step.

Switching capture methods also caused visible interruptions. The view could zoom out and back in around a shot. The logs didn't show repeated zoom commands during the aligned test, so I suspect switching between the two capture methods caused it, but that wasn't confirmed.

The second shutter press was another unresolved problem. The button registered the tap, but the app never reached the code that takes the photo. A successful first shot didn't mean consecutive shots worked.

## Gallery and language

Opening Gallery initially froze the copy. A replacement handoff let it open a completed photo in the phone's Gallery app, but the Camera thumbnail could still show an older image or say a photo hadn't finished saving. I hadn't resolved all of that behaviour when I stopped.

The movement instructions were in English, while the generated descriptions were in Chinese. One caption translated to “The frames stand beside the window, creating a more focused mood.” It described the scene; it wasn't an error.

Changing one embedded instruction to English didn't change the output, so I reverted that change. English descriptions remained unfinished.

## Where I stopped

| Function | Result |
| --- | --- |
| Framing suggestions | Worked on live camera input |
| Positioning guide and automatic zoom | Worked, with zoom checked against camera data |
| Viewfinder | Worked with HDR preview disabled |
| Taking photos | Individual full-resolution photos saved |
| Consecutive photos | Not reliable |
| Original image quality and colours | Not preserved |
| Lens changes during capture | Could visibly jump |
| Gallery | Opened saved photos, but thumbnail and saving status still had problems |
| English descriptions | Not working |
| Video, RAW and other modes | Not fully tested |

The tested X9 could run the framing software. That doesn't establish that OPPO could enable the complete feature on every X9 with one setting, because this version needed donor files and several repairs.

I don't think the remaining result was worth using instead of the original Camera. Fixing it would mean more work on photo processing, lens changes and how the app prepares for the next shot, with no demonstrated way to preserve the original quality. The investigation doesn't establish that root is necessary or that root would fix those problems.

## Source and records

The [research repository](https://github.com/n810K/oppo-lumo-framing-research) contains the experimental source and [detailed technical notes](https://github.com/n810K/oppo-lumo-framing-research/blob/main/docs/technical-notes.md), including the configuration names, model versions and recorded measurements.

There isn't a working camera release to download. The build scripts depend on earlier modified files and vendor assets that aren't included, so they aren't a complete build from the original APK. OPPO's APKs, firmware, models, signing keys and my photos aren't published.

The [OnePlus Clock port](https://n810studios.com/research/oppo-clock-port) ran into a related restriction: installing a copy didn't give it access to the services the original app could use.
