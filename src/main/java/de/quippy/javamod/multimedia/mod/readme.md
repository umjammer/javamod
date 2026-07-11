# de.quippy.multimedia.mod

### Status

#### javamod

| name | status | ext             | tracker    | comment                                                                             |
|------|:------:|-----------------|------------|-------------------------------------------------------------------------------------|
| MOD  |   ✅️   | MOD,STK,NST,WOW | pro        | ProTracker(mod), SoundTracker and compatible(stk), Mod's Grave(wow) etc.            |
| XM   |   ✅️   | XM              | xm         | FastTracker 2                                                                       |
| FAR  |   ✅️   | FAR             | frandole   | Farandole Composer                                                                  |
| MTM  |   ✅️   | MTM             | multi      | MultiTracker                                                                        |
| STM  |   ✅️   | STM,STS         | scream old | ScreamTracker 2                                                                     |
| STX  |   ✅️   | STX             | scream stx | Scream Tracker Music Interface Kit                                                  |
| S3M  |   ✅️   | S3M             | scream     | ScreamTracker 3                                                                     |
| IT   |   ✅️   | IT/MPTM         | implulse   | Impulse Tracker, OpenMPT                                                            |
| MO3  |   ✅️   | MO3             | mo3        | Un4seen MO3 compressed modules (IT/S3M/XM/MOD/MTM, MP3/Ogg/delta samples)           |
| XRNS |   ✅️   | XRNS            | renoise    | Renoise song (zip: Song.xml + flac/ogg/wav samples), converted to XM à la xrns2xmod |
| AON  |   ✅️   | AON,AON8        | artofnoise | Art Of Noise (Amiga 4/8 voices), converted to XM; synth wave tables approximated    |

 - PowerPacker ... compression

#### MODPLUG

```
MOD   ProTracker (Amiga)
STM   ScreamTracker 2
S3M   ScreamTracker 3
XM    FastTracker 2
IT    Impulse Tracker
669   Composer 669
AMF   ASYLUM Music Format / DSMI Advanced Music Format
AMS   Extreme's Tracker / Velvet Studio
DBM   Digi Booster Pro
DMF   X-Tracker
DSM   DSIK Format
FAR   Farandole Composer
MDL   DigiTrakker
MED   OctaMED (Amiga)
MTM   MultiTracker
OKT   Oktalyzer
PTM   PolyTracker
ULT   UltraTracker
UMX   Unreal Music Package
MT2   MadTracker 2
PSM   Epic Megagames MASI
```

#### OpenMPT

```
667                  Composer 667
669                  Composer 669 / UNIS 669
AMF                  ASYLUM Music Format
AMF/DMF              DSMI Advanced Music Format
AMS                  Extreme's Tracker / Velvet Studio
C67                  CDFM / Composer 670
CBA                  Chuck Biscuits / Black Artist
DBM                  Digi Booster Pro
DIGI                 Digi Booster
DMF                  X-Tracker
DSM                  DSIK Format
DSM                  Dynamic Studio
DSym                 Digital Symphony
DTM                  Digital Tracker / Digital Home Studio
ETX                  EasyTrax
FAR                  Farandole Composer
FC/FC13/FC14/SMOD    Future Composer
FMT                  Davey W. Taylor's FM Tracker
FTM                  Face The Music
GDM                  General Digital Music
GMC                  Game Music Creator
GTK/GT2              Graoumf Tracker
ICE/ST26             Ice Tracker / SoundTracker 2.6
IMF                  Imago Orpheus
IMS                  Images Music System
IT                   Impulse Tracker
ITP                  Impulse Tracker Project
J2B                  Jazz Jackrabbit 2 music format
M15/STK              SoundTracker and compatible
MDL                  DigiTrakker
MED                  OctaMED
MO3                  MO3 compressed modules
MOD                  ChipTracker
MOD                  ProTracker / NoiseTracker / etc. 1 - 99 channels
MOD                  TCB Tracker
MOD                  UNIC Tracker v1
MPTM                 OpenMPT
MT2                  MadTracker 2
MTM                  MultiTracker
MUS                  Psycho Pinball / Micro Machines 2 music format
OKT                  Oktalyzer
OXM                  OggMod-compressed XM files
PLM                  Disorder Tracker 2
PSM                  Epic Megagames MASI
PT36                 ProTracker 3.6 IFF
PTM                  PolyTracker
PUMA                 PumaTracker
RTM                  RealTracker
S3M                  Scream Tracker 3
SFX/SFX2/MMS         SoundFX / MultiMedia Sound
STM                  Scream Tracker 2
STX                  Scream Tracker Music Interface Kit
STP                  SoundTracker Pro 2
SymMOD               Symphonie / Symphonie Pro
ULT                  UltraTracker
UMX                  Unreal Music Package
WOW                  Mod's Grave
XM                   FastTracker 2
XMF                  Astroidea XMF
Compressed modules in ZIP / LHA / RAR / GZ archives, as well as various module-specific and other legacy compression formats (MMCMP, PowerPacker, XPK, Pack-Ice and many others)
```

#### real exts under mods dir

- .dm2 ... Delta Music Module (https://github.com/neumatho/NostalgicPlayer)
- .med ... MED (https://github.com/neumatho/NostalgicPlayer)
- .umx ... Module Converter (https://github.com/neumatho/NostalgicPlayer)
- .rns ... Renoise <1.8 binary format ("RNS0xx" chunks), proprietary and undocumented - not supported (xrns2xmod cannot read it either)

## References

- [OpenMPT](https://github.com/OpenMPT/openmpt/)
- [NostalgicPlayer](https://github.com/neumatho/NostalgicPlayer)
- [OpenCubicPlayer](https://github.com/mywave82/opencubicplayer)
- https://github.com/fstarred/xrns2xmod (xrns)
- samples
    * https://nostalgicplayer.dk/modules/format/mo3/4 (mo3)
