# Oakkar VPN (Android)

Key ထည့်သုံးရသော VPN app။ Key ကို paste လုပ်ပြီး Home မှ ချိတ်ဆက်ပါ။

## ပါဝင်သည်များ
- Home: ချိတ်/ဖြတ် ခလုတ်၊ ကြာချိန်၊ speed၊ Data သုံးစွဲမှု (ဒီနေ့ / ဒီလ / စုစုပေါင်း)
- Servers: key စာရင်း၊ ရွေးချယ်၊ ဖျက်
- Key ထည့်ရန်: vmess, vless, trojan, ss (Outline ပါ), ssconf (Outline dynamic key), base64 စာသား၊ Outline JSON
- Xray core + Android VpnService (tun)

## မထောက်ပံ့သေးသည်များ
- hysteria2, tuic (parse လုပ်ပေးသော်လည်း မချိတ်နိုင်သေး)
- Shadowsocks plugin
- QR scan၊ subscription URL ကို app ထဲမှ ဒေါင်းခြင်း

## ဤ source ၏ အခြေအနေ
ရေးထားပြီးသာ ဖြစ်ပြီး compile မလုပ်ရသေးပါ၊ ဖုန်းပေါ်တွင်လည်း မစမ်းရသေးပါ။ ပထမဆုံး build တွင် အမှားထွက်နိုင်ပါသည်။
Xray core နှင့် ဆက်သွယ်သော code အားလုံးသည် `CoreBridge.kt` တစ်ဖိုင်တည်းတွင် ရှိသည်။ libv2ray version ကွာလျှင် ထိုဖိုင်ကိုသာ ပြင်ရမည်။

## APK ထုတ်နည်း

### A) GitHub Actions (computer မလိုပါ)
1. ဤ folder တစ်ခုလုံးကို GitHub repository အသစ်တစ်ခုသို့ upload လုပ်ပါ (`main` branch)။
2. Repository ၏ **Actions** tab → **Build APK** → **Run workflow**။
3. ပြီးလျှင် run ထဲမှ `oakkar-vpn-apk` artifact ကို ဒေါင်းပြီး APK ကို ဖုန်းတွင် install လုပ်ပါ။

### B) Android Studio
1. [AndroidLibXrayLite releases](https://github.com/2dust/AndroidLibXrayLite/releases) မှ `libv2ray.aar` ကို ဒေါင်းပြီး `app/libs/` ထဲ ထည့်ပါ။
2. Android Studio (JDK 17) ဖြင့် ဤ folder ကို ဖွင့်ပြီး **Build > Build APK**။

## ဖိုင်များ
| ဖိုင် | လုပ်ဆောင်ချက် |
|---|---|
| `KeyParser.kt` | Key များကို ဖတ်၊ စစ် |
| `XrayConfig.kt` | Key မှ Xray config ထုတ် |
| `CoreBridge.kt` | Xray core ကို စ/ရပ် |
| `OakVpnService.kt` | VpnService, tun, notification |
| `MainViewModel.kt`, `KeyStore.kt` | Key သိမ်း၊ ရွေး၊ data တိုင်း |
| `MainActivity.kt` | UI (Home, Servers, Key ထည့်ရန်, ဆက်တင်) |
| `ConnectButton.kt` | Home အလယ်က အဝိုင်းကြီး animation (မချိတ်ရသေး: ring အသက်ရှူ၊ ချိတ်နေစဉ်: အလင်းကွင်းလည်၊ ချိတ်ပြီး: ripple ပြန့်၊ ချိတ်မိသွားတဲ့ အခိုက်မှာ ring ပေါက်ကွဲ + မီးပွား + 🌐 ကမ္ဘာလုံးပုံ) |
| `SpeedCard.kt` | Download / Upload card animation (မြှားစီးဆင်း၊ နံပါတ်ချောချောလေး ပြောင်း၊ ၃၀ စက္ကန့် graph) |
| `Splash.kt` | App ဖွင့်ချိန် ၃.၈ စက္ကန့် animation (မှောင်ထဲ ကာတွန်းကောင်လေး လမ်းလျှောက်ဝင်လာပြီး ရပ်၊ ဓါတ်မီးထိုး၊ မီးရောင်အဝိုင်းကြီးက logo ကို ဖော်၊ မီးလင်း၊ ကောင်လေး logo ရှေ့က လျှောက်ထွက်၊ နှိပ်လျှင် ကျော်နိုင်) |
