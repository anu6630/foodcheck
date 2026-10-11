#!/usr/bin/env python3
"""Generate Play Console store-listing translation CSV/XLSX."""

from __future__ import annotations

import csv
import shutil
from pathlib import Path

try:
    from openpyxl import Workbook
    from openpyxl.styles import Alignment, Font
except ImportError:  # pragma: no cover
    Workbook = None  # type: ignore

ROOT = Path(__file__).resolve().parent
DOWNLOADS = Path.home() / "Downloads"

EN_TITLE = "LifeX: Food Ingredients Check"
EN_SHORT = (
    "Scan food labels to spot banned & restricted ingredients"
    "\u2014know what\u2019s in a pack."
)
EN_FULL = """LifeX Food helps you understand what\u2019s in packaged food\u2014before you buy or serve it.

Snap a photo of the ingredients label. We read the pack, match substances, and show regulation signals for your market (starting with India)\u2014including banned and restricted additives\u2014not vague \u201chealth scores.\u201d

What you can do
\u2022 Scan ingredient labels with your camera or photo library
\u2022 See clear substance tags with watch signals and regulation context
\u2022 Search products and ingredients in the catalog
\u2022 Save packs to My list on this device with your stance (Avoid, Occasional, OK, Unsure)
\u2022 Optionally tag items for Kids, Adults, Elderly, or Home

Built for real label decisions
LifeX Food is made for shoppers who want clarity on additives and restrictions\u2014not influencer rankings. Your saved list stays private to your device.

Important
LifeX Food is an information tool. It does not provide medical advice. Always follow local regulations and your own judgment for dietary needs.

Know what\u2019s banned, anywhere."""


def full_template(
    intro: str,
    scan: str,
    can_do_title: str,
    bullets: list[str],
    important_title: str,
    important_body: str,
) -> str:
    lines = [
        intro,
        "",
        scan,
        "",
        can_do_title,
        *[f"\u2022 {b}" for b in bullets],
        "",
        important_title,
        important_body,
        "",
        "Know what\u2019s banned, anywhere.",
    ]
    return "\n".join(lines)


ROWS: list[tuple[str, str, str, str]] = [
    ("en-US", EN_TITLE, EN_SHORT, EN_FULL),
    # India / South Asia
    (
        "hi-IN",
        "LifeX: खाद्य सामग्री जाँच",
        "लेबल स्कैन करें—प्रतिबंधित सामग्री पहचानें। पैक में क्या है, जानें।",
        full_template(
            "LifeX Food पैक्ड भोजन में क्या है—खरीदने या परोसने से पहले समझने में मदद करता है।",
            "सामग्री लेबल की फ़ोटो लें। हम पैक पढ़ते हैं, पदार्थों का मिलान करते हैं, और आपके बाज़ार (भारत से शुरू) के लिए नियमन संकेत दिखाते हैं—प्रतिबंधित एडिटिव्स सहित—अस्पष्ट “हेल्थ स्कोर” नहीं।",
            "आप क्या कर सकते हैं",
            [
                "कैमरा या गैलरी से सामग्री लेबल स्कैन करें",
                "वॉच सिग्नल और नियमन संदर्भ के साथ स्पष्ट टैग देखें",
                "कैटलॉग में उत्पाद और सामग्री खोजें",
                "इस डिवाइस पर My list में पैक सेव करें (Avoid, Occasional, OK, Unsure)",
                "Kids, Adults, Elderly या Home टैग जोड़ें",
            ],
            "महत्वपूर्ण",
            "LifeX Food एक सूचना उपकरण है। यह चिकित्सा सलाह नहीं है। स्थानीय नियमों और अपनी ज़रूरतों का पालन करें।",
        ),
    ),
    (
        "bn-BD",
        "LifeX: খাদ্য উপাদান যাচাই",
        "লেবেল স্ক্যান করুন—নিষিদ্ধ/সীমিত উপাদান চিনুন। প্যাকে কী আছে জানুন।",
        full_template(
            "LifeX Food প্যাকেটজাত খাবারে কী আছে—কেনার বা পরিবেশনের আগে বুঝতে সাহায্য করে।",
            "উপাদানের লেবেলের ছবি তুলুন। আমরা প্যাক পড়ি, পদার্থ মিলাই এবং আপনার বাজারের (ভারত থেকে শুরু) নিয়ন্ত্রণ সংকেত দেখাই—নিষিদ্ধ ও সীমিত অ্যাডিটিভসহ—অস্পষ্ট “হেলথ স্কোর” নয়।",
            "আপনি যা করতে পারেন",
            [
                "ক্যামেরা বা গ্যালারি দিয়ে লেবেল স্ক্যান করুন",
                "সতর্কতা ও নিয়ন্ত্রণ প্রসঙ্গসহ স্পষ্ট ট্যাগ দেখুন",
                "ক্যাটালগে পণ্য ও উপাদান খুঁজুন",
                "এই ডিভাইসে My list-এ প্যাক সেভ করুন",
                "Kids, Adults, Elderly বা Home ট্যাগ দিন",
            ],
            "গুরুত্বপূর্ণ",
            "LifeX Food তথ্যের টুল—চিকিৎসা পরামর্শ নয়। স্থানীয় নিয়ম ও নিজের বিচার অনুসরণ করুন।",
        ),
    ),
    (
        "te-IN",
        "LifeX: ఆహార పదార్థాలు",
        "లేబుల్ స్కాన్ చేసి నిషేధి�్కాన్ చేసి నిషేధిత పదార్థాలు గుర్తించండి.",
        full_template(
            "LifeX Food ప్యాక్ చేసిన ఆహారంలో ఏముందో—కొనడానికి లేదా వడ్డించడానికి ముందు అర్థం చేసుకోవడానికి సహాయపడు��ుంది.",
            "పదార్థాల లేబుల్ �ం చేసుకోవడానికి సహాయపడుతుంది.",
            "పదార్థాల లేబుల్ ఫోటో తీయండి. మేము ప్యాక్ చదివి, పదార్థాలను సరిపోల్చి, మీ మార్కెట్ (భారత్ నుంచి) కోసం ని�్ నుంచి) కోసం నియంత్రణ సంకేతాలు చూపుతాము—నిషేధిత/పరిమిత యాడిటివ్‌లతో స�ిత యాడిటివ్‌లతో సహా.",
            "మీరు చేయగలిగేది",
            [
                "కెమెరా లేదా గ్యాలరీతో లేబుల్ స్కాన్",
                "వాచ్ సిగ్నల్స్ + నియం� "వాచ్ సిగ్నల్స్ + నియంత్రణ సందర్భంతో ట్యాగ్‌లు",
                "కేటలాగ్‌లో ఉత్పత్తులు/పదార్థాలు వెతకండి",
                "ఈ డివైస్‌లో My listకి సేవ్ చేయండి",
                "Kids, Adults, Elderly �ో My listకి సేవ్ చేయండి",
                "Kids, Adults, Elderly లేదా Home ట్యాగ్‌లు",
            ],
            "ముఖ్యం",
            "LifeX Food సమాచార సాధనం—వైద్య సలహా కాదు. స్థానిక నియమాలు మరియు మీ అవసరాలు పాటించండి.",
        ),
    ),
    (
        "mr-IN",
        "LifeX: अन्न घटक तपासणी",
        "लेबल स्कॅन करा—बंदी/मर्यादित घटक ओळखा. पॅकमध्ये काय आहे ते जाणा.",
        full_template(
            "LifeX Food पॅक्ड अन्नात काय आहे—खरेदी किंवा वापरण्यापूर्वी समजून घेण्यास मदत करते.",
            "घटकांच्या लेबलचा फोटो काढा. आम्ही पॅक वाचतो, पदार्थ जुळवतो आणि तुमच्या बाजारासाठी (भारत पासून) नियमनाचे संकेत दाखवतो.",
            "तुम्ही काय करू शकता",
            [
                "कॅमेरा/गॅलरीने लेबल स्कॅन",
                "वॉच सिग्नल आणि नियम संदर्भ असलेले टॅग",
                "कॅटलॉगमध्ये उत्पादने व घटक शोधा",
                "या डिव्हाइसवर My list मध्ये जतन करा",
                "Kids, Adults, Elderly किंवा Home टॅग",
            ],
            "महत्त्वाचे",
            "LifeX Food माहिती साधन आहे—वैद्यकीय सल्ला नाही. स्थानिक नियम आणि स्वतःचा निर्णय पाळा.",
        ),
    ),
    (
        "ta-IN",
        "LifeX: உணவுப் பொருள் சோதனை",
        "லேபிளை ஸ்கேன் செய்து தடை/கட்டுப்பாட்டு பொருட்களை அறியுங்கள்.",
        full_template(
            "LifeX Food பேக் செய்யப்பட்ட உணவில் என்ன உள்ளது—வாங்குவதற்கு அல்லது பரிமாறுவதற்கு முன் புரிந்துகொள்ள உதவுகிறது.",
            "பொருட்கள் லேபிளின் புகைப்படம் எடுங்கள். நாங்கள் பேக்கைப் படித்து பொருட்களை பொருத்தி, உங்கள் சந்தைக்கான (இந்தியா முதல்) ஒழுங்குமுறை சமிக்ஞைகளை காட்டுகிறோம்.",
            "நீங்கள் செய்யக்கூடியவை",
            [
                "கேமரா/கேலரியில் லேபிள் ஸ்கேன்",
                "வாட்ச் சிக்னல்கள் + ஒழுங்குமுறை சூழலுடன் டேக்குகள்",
                "கேட்டலாக்கில் தயாரிப்புகள்/பொருட்கள் தேடல்",
                "இந்த சாதனத்தில் My list-இல் சேமிக்கவும்",
                "Kids, Adults, Elderly அல்லது Home டேக்குகள்",
            ],
            "முக்கியம்",
            "LifeX Food தகவல் கருவி—மருத்துவ ஆலோசனை அல்ல. உள்ளூர் விதிகளையும் உங்கள் தேவைகளையும் பின்பற்றுங்கள்.",
        ),
    ),
    (
        "gu-IN",
        "LifeX: ખોરાક ઘટક તપાસ",
        "લેબલ સ્કેન કરો—પ્રતિબંધિત ઘટકો �્રતિબંધિત ઘટકો ઓળખો. પેકમાં શું છે તે જાણો.",
        full_template(
            "LifeX Food પેક્ડ �ે જાણો.",
        full_template(
            "LifeX Food પેક્ડ ખોરાકમાં શું છે—ખરીદતા કે પીરસતા પહેલાં સમજવામાં મદદ કરે છે.",
            "ઘટકોના �દ કરે છે.",
            "ઘટકોના લેબલનો ફોટો લો. અમે પેક વાંચીએ છીએ, પદાર્થો મેચ કરીએ છીએ અને તમારા બ��ાર (ભારતથી શરૂ) માટે નિયમન સંકે� કરીએ છીએ અને તમારા બજાર (ભારતથી શરૂ) માટે નિયમન સંકેત બતાવીએ છીએ.",
            "તમે શું કરી શકો",
            [
                "કેમેરા/ગેલરીથી લેબલ સ્કેન",
                "વૉ�� સિગ્નલ અને નિયમન સંદર્ભ સાથે �ી લેબલ સ્કેન",
                "વૉચ સિગ્નલ અને નિયમન સંદર્ભ સાથે ટેગ્સ",
                "કેટલોગમાં ઉત્પાદનો/ઘટકો શોધો",
                "આ �નો/ઘટકો શોધો",
                "આ ડિવાઇસ પર My listમાં સેવ કરો",
                "Kids, Adults, Elderly અથવા Home ટેગ્સ",
            ],
            "મહત્વનું",
            "LifeX Food માહિતી સાધન છે—તબીબી સલાહ નથી. સ્થાનિક નિયમો અને તમારો નિર્ણય અનુસરો.",
        ),
    ),
    (
        "kn-IN",
        "LifeX: ಆಹಾರ ಪದಾರ್ಥ ಪರಿಶೀಲನೆ",
        "ಲೇಬಲ್ ಸ್ಕ್ಯಾ�ರಿಶೀಲನೆ",
        "ಲೇಬಲ್ ಸ್ಕ್ಯಾನ್ ಮಾಡಿ—ನಿಷೇಧಿತ/ನಿರ್ಬಂಧಿತ ಪದಾರ್ಥಗಳನ್ನು ಗುರುತಿಸಿ.",
        full_template(
            "LifeX Food ಪ್ಯಾಕ್ ಮಾಡಿದ ಆಹಾರದಲ್ಲಿ ಏನಿದೆ—ಖರೀದಿ ಅಥವಾ ಬಡಿಸುವ ಮೊದಲು ಅರ್ಥಮಾಡಿಕೊಳ್ಳಲು ಸಹಾಯ ಮಾಡುತ್ತದೆ.",
            "ಪದಾರ್ಥಗಳ ಲೇಬಲ್ ಫೋಟೋ ತೆಗೆಯಿರಿ. ನಾವು ಪ್ಯಾಕ್ ಓದಿ, ಪದಾರ್�ೆಯಿರಿ. ನಾವು ಪ್ಯಾಕ್ ಓದಿ, ಪದಾರ್ಥಗಳನ್ನು ಹೊಂದಿಸಿ, ನಿಮ್ಮ ಮಾರುಕಟ್ಟೆಗೆ (ಭಾರತದಿಂದ) ನಿಯಂತ್ರಣ ಸಂಕೇತಗಳನ್ನು ತೋರಿಸುತ್ತೇವೆ.",
            "ನೀವು ಮಾಡಬಹುದಾದುದು",
            [
                "ಕ್ಯಾಮೆರಾ/ಗ್ಯಾ�ುದಾದುದು",
            [
                "ಕ್ಯಾಮೆರಾ/ಗ್ಯಾಲರಿಯಿಂದ ಲೇಬಲ್ ಸ್ಕ್ಯಾನ್",
                "ವಾಚ್ ಸಿಗ್ನಲ್ + ನಿಯಂತ್ರಣ ಸಂದರ್ಭದೊಂದಿಗೆ ಟ್ಯಾಗ್‌ಗಳು",
                "ಕ್ಯಾಟಲಾಗ್‌ನಲ್ಲಿ ಉತ್ಪನ್ನ/ಪದಾರ್ಥ ಹುಡುಕಿ",
                "ಈ ಸಾಧನದಲ್ಲಿ My listಗೆ ಉಳಿಸಿ",
                "Kids, Adults, Elderly ಅಥವಾ Home ಟ್ಯಾಗ್‌ಗಳು",
            ],
            "ಮುಖ್ಯ",
            "LifeX Food ಮಾಹಿತಿ ಸಾಧನ—ವೈದ್ಯಕೀಯ ಸಲಹೆ ಅಲ್ಲ. ಸ್ಥಳೀಯ ನಿಯಮಗಳು ಮತ್ತು ನಿಮ್ಮ ಅಗತ್ಯಗಳನ್ನು ಅನುಸರಿಸಿ.",
        ),
    ),
    (
        "ml-IN",
        "LifeX: ��ക്ഷ്യ ഘടക പരി�ನುಸರಿಸಿ.",
        ),
    ),
    (
        "ml-IN",
        "LifeX: ഭക്ഷ്യ ഘടക പരിശോധന",
        "ലേബൽ സ്കാൻ ചെയ്ത് നിരോധിത/നിയന്ത്രിത �് നിരോധിത/നിയന്ത്രിത ഘടകങ്ങൾ കണ്ടെത്തൂ.",
        full_template(
            "LifeX Food പായ്ക്ക് ചെയ്ത ഭക്ഷണത്തിൽ എന്തുണ്ടെന്ന്—വാങ്ങുന്നതിനോ വിളമ്പുന്നതിനോ മുമ്പ് മനസ്സിലാക്കാൻ സഹായിക്കുന്നു.",
            "ഘടക ലേബലിന്റെ ഫോട്ടോ എടുക്കൂ. ഞങ്ങൾ പായ്ക്ക് വായിച്ച് പദാർഥങ്ങൾ പൊരുത്തപ്പെടുത്തി, നിങ്ങളുടെ വിപണിക്ക് (ഇന്ത്യ മുതൽ) നിയന്ത്രണ സൂചനകൾ കാണിക്കുന്നു.",
            "നിങ്��ൾക്ക് ചെയ്യാവുന്നത്",
            [
                "ക്�ണ സൂചനകൾ കാണിക്കുന്നു.",
            "നിങ്ങൾക്ക് ചെയ്യാവുന്നത്",
            [
                "ക്യാമറ/ഗാലറി ഉപയോഗിച്ച് ലേബൽ സ്കാൻ",
                "വാച്ച് സി�ഗിച്ച് ലേബൽ സ്കാൻ",
                "വാച്ച് സിഗ്നലുകളും നിയന്ത്രണ സന്ദർഭവും ഉള്ള ടാഗുകൾ",
                "കാറ്റലോഗിൽ ഉൽപ്പന്നങ്ങൾ/ഘടകങ്ങൾ തിരയുക",
                "ഈ ഉപകരണത്തിൽ My list-ൽ സേവ് ചെയ്യുക",
                "Kids, Adults, Elderly അല്ലെങ്കിൽ Home ടാഗുകൾ",
            ],
            "പ്രധാനം",
            "LifeX Food വിവര ഉപകരണമാണ്—വൈദ്യോപദേശമല്ല. പ്രാദേ�ൈദ്യോപദേശമല്ല. പ്രാദേശിക നിയമങ്ങളും നിങ്ങളുടെ ആവശ്യങ്ങളും പാലിക്കുക.",
        ),
    ),
    (
        "pa-IN",
        "LifeX: �ാലിക്കുക.",
        ),
    ),
    (
        "pa-IN",
        "LifeX: ਖੁਰਾਕ ਘਟਕ ਜਾਂਚ",
        "ਲੇਬਲ ਸਕੈਨ ਕਰੋ—ਪਾਬੰਦ/ਸੀ�ਲ ਸਕੈਨ ਕਰੋ—ਪਾਬੰਦ/ਸੀਮਿਤ ਘਟਕ ਪਛਾਣੋ। ਪੈਕ ਵਿੱਚ ਕੀ ਹੈ ਜਾਣੋ।",
        full_template(
            "LifeX Food ਪੈਕ ਕੀਤੇ ਖਾਣੇ ਵਿੱਚ ਕੀ ਹੈ—ਖਰੀਦਣ ਜਾਂ ਪਰੋਸਣ ਤੋਂ ਪਹਿਲਾਂ ਸਮਝ�ੋਂ ਪਹਿਲਾਂ ਸਮਝਣ ਵਿੱਚ ਮਦਦ ਕਰਦਾ ਹੈ।",
            "ਘਟਕਾਂ ਦੇ ਲੇਬਲ ਦੀ ਫੋ�            "ਘਟਕਾਂ ਦੇ ਲੇਬਲ ਦੀ ਫੋਟੋ ਲਓ। ਅਸੀਂ ਪੈਕ ਪੜ੍ਹਦੇ ਹਾਂ, ਪਦਾਰਥ ਮਿਲਾਉਂਦੇ ਹਾਂ ਅਤੇ ਤ��ਹਾਡੇ ਬਾਜ਼ਾਰ (ਭਾ��ਤ ਤੋਂ) ਲਈ ਨਿਯਮ ਸੰ�ਾਉਂਦੇ ਹਾਂ ਅਤੇ ਤੁਹਾਡੇ ਬਾਜ਼ਾਰ (ਭਾਰਤ ਤੋਂ) ਲਈ ਨਿਯਮ ਸੰਕੇਤ ਦਿਖਾਉਂਦੇ ਹਾਂ।",
            "ਤੁਸੀਂ ਕੀ ਕਰ ਸਕਦੇ ਹੋ",
            [
                "ਕੈਮਰਾ/ਗੈਲਰੀ ਨਾਲ ਲੇਬਲ ਸਕੈਨ",
                "ਵਾਚ ਸਿਗਨਲ ਅਤੇ ਨਿਯਮ ਸੰਦਰਭ ਵਾਲੇ ਟੈਗ",
                "ਕੈਟਾਲਾ�ਰਭ ਵਾਲੇ ਟੈਗ",
                "ਕੈਟਾਲਾਗ ਵਿੱਚ ਉਤਪਾਦ/ਘਟਕ ਖੋਜੋ",
                "ਇਸ ਡਿਵਾਈਸ ’ਤੇ My list ��ਿੱਚ ਸੇਵ ਕਰੋ",
                "Kids, Adults, Elderly ਜਾਂ Home ਟੈ�ੋ",
                "ਇਸ ਡਿਵਾਈਸ ’ਤੇ My list ਵਿੱਚ ਸੇਵ ਕਰੋ",
                "Kids, Adults, Elderly ਜਾਂ Home ਟੈਗ",
            ],
            "ਮਹੱਤਵਪੂਰਨ",
            "LifeX Food ਜਾਣਕਾਰੀ ਟੂਲ ਹ��—ਡਾਕਟਰੀ ਸਲਾਹ ਨ�ਨ",
            "LifeX Food ਜਾਣਕਾਰੀ ਟੂਲ ਹੈ—ਡਾਕਟਰੀ ਸਲਾਹ ਨਹੀਂ। ਸਥਾਨਕ ਨਿਯਮਾਂ ਅਤੇ ਆਪਣੇ ਫੈਸਲੇ ਦੀ ਪਾ��ਣਾ ਕਰੋ।",
        ),
    ),
    (
        "or-IN",
        "LifeX: ଖା��୍ୟ ଉପାଦା�ਪਣੇ ਫੈਸਲੇ ਦੀ ਪਾਲਣਾ ਕਰੋ।",
        ),
    ),
    (
        "or-IN",
        "LifeX: ଖାଦ୍ୟ ଉପାଦାନ ଯାଞ୍ଚ",
        "ଲେବଲ୍ ସ୍କାନ୍ କରନ୍ତ�ବଲ୍ ସ୍କାନ୍ କରନ୍ତୁ—ନିଷେଧିତ/ସୀମିତ ଉପାଦାନ ଚିହ୍ନଟ କରନ୍ତୁ।",
        full_template(
            "LifeX Food ପ୍ୟାକ୍ ଖାଦ୍ୟରେ କ’ଣ ଅଛି—କିଣିବା କିମ୍ବା ପରିବେଷଣ ପୂର୍ବରୁ ବୁଝିବାରେ ସାହାଯ୍ୟ କରେ।",
            "ଉପାଦା�ହାଯ୍ୟ କରେ।",
            "ଉପାଦାନ ଲେବଲର ଫଟୋ ନିଅନ୍ତୁ। ଆମେ �ିଅନ୍ତୁ। ଆମେ ପ୍ୟାକ୍ ପଢ଼ି ପଦାର୍ଥ ମେଳ କରୁ ଏବଂ ଆପଣଙ୍କ ବଜାର ପା�ଂ ଆପଣଙ୍କ ବଜାର ପାଇଁ ନିୟମ ସଙ୍କେତ ଦେଖାଉ।",
            "ଆପଣ କ’ଣ କରିପାରିବେ",
            [
                "କ୍ୟାମେରା/ଗ୍ୟାଲେରୀରେ ଲେବଲ୍ ସ୍କାନ୍",
                "ୱାଚ୍ ସିଗ୍� ସ୍କାନ୍",
                "ୱାଚ୍ ସିଗ୍ନାଲ୍ ଏବଂ ନିୟମ ପ୍ରସଙ୍ଗ ସହ ଟ୍ୟା�ସଙ୍ଗ ସହ ଟ୍ୟାଗ୍",
                "କାଟାଲଗରେ ଉତ୍ପାଦ/ଉପାଦାନ ଖୋଜ��୍ତୁ",
                "ଏହି ଡିଭାଇ�ପାଦ/ଉପାଦାନ ଖୋଜନ୍ତୁ",
                "ଏହି ଡିଭାଇସରେ My listରେ ସେଭ୍ କରନ୍ତୁ",
            ],
            "ଗୁରୁତ୍��ପୂର୍ଣ୍ଣ",
            "LifeX Food ସ�            ],
            "ଗୁରୁତ୍ୱପୂର୍ଣ୍ଣ",
            "LifeX Food ସୂଚନା ଉପକରଣ—ଚିକିତ୍ସା ପରା�ଚିକିତ୍ସା ପରାମର୍ଶ ନୁହେଁ। ସ୍ଥାନୀୟ ନିୟମ ଏବ�୍ଥାନୀୟ ନିୟମ ଏବଂ ନିଜ ବିଚାର ପାଳନ କରନ୍ତୁ।",
        ),
    ),
    (
        "as-IN",
        "LifeX: খাদ্য উপাদান পৰীক্ষা",
        "লেবেল স্কেন কৰক—নিষিদ্ধ/সীমিত উপাদান চিনাক্ত কৰক।",
        full_template(
            "LifeX Food পেকেট খাদ্যত কি আছে—কিনিবলৈ বা পৰিবেশন কৰাৰ আগেয়ে বুজিবলৈ সহায় কৰে।",
            "উপাদানৰ লেবেলৰ ফটো লওক। আমি পেক পঢ়োঁ, পদাৰ্থ মিলাওঁ আৰু আপোনাৰ বজাৰৰ বাবে নিয়ন্ত্ৰণ সংকেত দেখুৱাওঁ।",
            "আপুনি কি কৰিব পাৰে",
            [
                "কেমেৰা/গেলাৰীৰে লেবেল স্কেন",
                "ৱাচ ছিগনেল আৰু নিয়ন্ত্ৰণ প্ৰসংগৰ সৈতে টেগ",
                "কেটেলগতত সামগ্ৰী/উপাদান বিচাৰক",
                "এই ডিভাইচত My list-ত চেভ কৰক",
            ],
            "গুৰুত্বপূৰ্ণ",
            "LifeX Food তথ্য সঁজুলি—চিকিৎসা পৰামৰ্শ নহয়। স্থানীয় নিয়ম আৰু নিজৰ সিদ্ধান্ত মানি চলক।",
        ),
    ),
    (
        "ur",
        "LifeX: غذائی اجزاء چیک",
        "لیبل اسکین کریں—ممنوع/محدود اجزاء پہچانیں۔ پیک میں کیا ہے جانیں۔",
        full_template(
            "LifeX Food پیک شدہ کھانے میں کیا ہے—خریدنے یا پیش کرنے سے پہلے سمجھنے میں مدد کرتا ہے۔",
            "اجزاء والے لیبل کی تصویر لیں۔ ہم پیک پڑھتے ہیں، مادے ملاتے ہیں اور آپ کی مارکیٹ کے لیے ضابطہ کے اشارے دکھاتے ہیں۔",
            "آپ کیا کر سکتے ہیں",
            [
                "کیمرہ/گیلری سے لیبل اسکین",
                "واچ سگنلز اور ضابطہ سیاق کے ساتھ ٹیگز",
                "کیٹلاگ میں مصنوعات/اجزاء تلاش کریں",
                "اس ڈیوائس پر My list میں محفوظ کریں",
            ],
            "اہم",
            "LifeX Food معلوماتی ٹول ہے—طبی مشورہ نہیں۔ مقامی قوانین اور اپنے فیصلے کی پیروی کریں۔",
        ),
    ),
    # Strict local-language markets
    (
        "de-DE",
        "LifeX: Zutaten prüfen",
        "Etiketten scannen—verbotene & beschränkte Stoffe erkennen. Wissen, was drin ist.",
        full_template(
            "LifeX Food hilft dir zu verstehen, was in verpackten Lebensmitteln steckt—bevor du kaufst oder servierst.",
            "Fotografiere das Zutatenetikett. Wir lesen die Packung, ordnen Stoffe zu und zeigen Regulierungshinweise für deinen Markt (beginnend mit Indien)—inkl. verbotener und beschränkter Zusatzstoffe.",
            "Das kannst du tun",
            [
                "Etiketten mit Kamera oder Galerie scannen",
                "Klare Stoff-Tags mit Watch-Signalen und Regelkontext sehen",
                "Produkte und Inhaltsstoffe im Katalog suchen",
                "Packungen in My list auf diesem Gerät speichern",
                "Optional mit Kids, Adults, Elderly oder Home taggen",
            ],
            "Wichtig",
            "LifeX Food ist ein Informationstool und keine medizinische Beratung. Beachte lokale Vorschriften und deine eigenen Bedürfnisse.",
        ),
    ),
    (
        "fr-FR",
        "LifeX: Contrôle des ingrédients",
        "Scannez les étiquettes—repérez additifs interdits/restreints. Sachez ce qu’il y a dedans.",
        full_template(
            "LifeX Food vous aide à comprendre ce qu’il y a dans les aliments emballés—avant d’acheter ou de servir.",
            "Photographiez l’étiquette des ingrédients. Nous lisons le produit, associons les substances et affichons des signaux réglementaires pour votre marché (à commencer par l’Inde).",
            "Ce que vous pouvez faire",
            [
                "Scanner les étiquettes via appareil photo ou galerie",
                "Voir des tags clairs avec signaux d’alerte et contexte réglementaire",
                "Rechercher produits et ingrédients dans le catalogue",
                "Enregistrer des packs dans My list sur cet appareil",
                "Tags optionnels : Kids, Adults, Elderly, Home",
            ],
            "Important",
            "LifeX Food est un outil d’information, pas un avis médical. Respectez les règles locales et votre propre jugement.",
        ),
    ),
    (
        "es-ES",
        "LifeX: Revisar ingredientes",
        "Escanea etiquetas—detecta aditivos prohibidos o restringidos. Sepa qué hay dentro.",
        full_template(
            "LifeX Food te ayuda a entender qué hay en los alimentos envasados—antes de comprar o servir.",
            "Haz una foto de la etiqueta de ingredientes. Leemos el envase, emparejamos sustancias y mostramos señales regulatorias para tu mercado (empezando por India).",
            "Qué puedes hacer",
            [
                "Escanear etiquetas con cámara o galería",
                "Ver etiquetas claras con señales de vigilancia y contexto normativo",
                "Buscar productos e ingredientes en el catálogo",
                "Guardar packs en My list en este dispositivo",
                "Etiquetas opcionales: Kids, Adults, Elderly, Home",
            ],
            "Importante",
            "LifeX Food es una herramienta informativa, no consejo médico. Sigue la normativa local y tu criterio.",
        ),
    ),
    (
        "es-419",
        "LifeX: Revisar ingredientes",
        "Escanea etiquetas—detecta aditivos prohibidos o restringidos. Sepa qué hay dentro.",
        full_template(
            "LifeX Food te ayuda a entender qué hay en los alimentos empaquetados—antes de comprar o servir.",
            "Fotografía la etiqueta de ingredientes. Leemos el envase, emparejamos sustancias y mostramos señales regulatorias para tu mercado (empezando por India).",
            "Qué puedes hacer",
            [
                "Escanear etiquetas con cámara o galería",
                "Ver tags claros con señales de alerta y contexto normativo",
                "Buscar productos e ingredientes en el catálogo",
                "Guardar packs en My list en este dispositivo",
                "Tags opcionales: Kids, Adults, Elderly, Home",
            ],
            "Importante",
            "LifeX Food es una herramienta de información, no consejo médico. Sigue las normas locales y tu criterio.",
        ),
    ),
    (
        "pt-BR",
        "LifeX: Checar ingredientes",
        "Escaneie rótulos—detecte aditivos banidos/restritos. Saiba o que tem no pacote.",
        full_template(
            "LifeX Food ajuda você a entender o que há em alimentos embalados—antes de comprar ou servir.",
            "Fotografe o rótulo de ingredientes. Lemos a embalagem, associamos substâncias e mostramos sinais regulatórios para o seu mercado (começando pela Índia).",
            "O que você pode fazer",
            [
                "Escanear rótulos com câmera ou galeria",
                "Ver tags claras com sinais de alerta e contexto regulatório",
                "Buscar produtos e ingredientes no catálogo",
                "Salvar packs em My list neste dispositivo",
                "Tags opcionais: Kids, Adults, Elderly, Home",
            ],
            "Importante",
            "LifeX Food é uma ferramenta de informação, não aconselhamento médico. Siga as regras locais e o seu julgamento.",
        ),
    ),
    (
        "it-IT",
        "LifeX: Controllo ingredienti",
        "Scansiona le etichette—individua additivi vietati/ristretti. Sai cosa c’è dentro.",
        full_template(
            "LifeX Food ti aiuta a capire cosa c’è negli alimenti confezionati—prima di acquistare o servire.",
            "Fotografa l’etichetta degli ingredienti. Leggiamo la confezione, associamo le sostanze e mostriamo segnali regolatori per il tuo mercato (a partire dall’India).",
            "Cosa puoi fare",
            [
                "Scansionare etichette con fotocamera o galleria",
                "Vedere tag chiari con segnali di attenzione e contesto normativo",
                "Cercare prodotti e ingredienti nel catalogo",
                "Salvare pack in My list su questo dispositivo",
                "Tag opzionali: Kids, Adults, Elderly, Home",
            ],
            "Importante",
            "LifeX Food è uno strumento informativo, non un parere medico. Segui le norme locali e il tuo giudizio.",
        ),
    ),
    (
        "nl-NL",
        "LifeX: Ingrediënten check",
        "Scan etiketten—spot verboden/beperkte stoffen. Weet wat erin zit.",
        full_template(
            "LifeX Food helpt je te begrijpen wat er in verpakte voeding zit—voordat je koopt of serveert.",
            "Maak een foto van het ingrediëntenlabel. We lezen de verpakking, koppelen stoffen en tonen reguleringssignalen voor jouw markt (beginnend bij India).",
            "Wat je kunt doen",
            [
                "Labels scannen met camera of galerij",
                "Duidelijke tags met watch-signalen en regelcontext",
                "Producten en ingrediënten zoeken in de catalogus",
                "Packs opslaan in My list op dit apparaat",
                "Optionele tags: Kids, Adults, Elderly, Home",
            ],
            "Belangrijk",
            "LifeX Food is een informatietool, geen medisch advies. Volg lokale regels en je eigen oordeel.",
        ),
    ),
    (
        "pl-PL",
        "LifeX: Sprawdź składniki",
        "Skanuj etykiety—wykrywaj zakazane/ograniczone dodatki. Wiedz, co jest w środku.",
        full_template(
            "LifeX Food pomaga zrozumieć, co jest w żywności pakowanej—zanim kupisz lub podasz.",
            "Zrób zdjęcie etykiety składników. Odczytujemy opakowanie, dopasowujemy substancje i pokazujemy sygnały regulacyjne dla Twojego rynku (zaczynając od Indii).",
            "Co możesz zrobić",
            [
                "Skanować etykiety aparatem lub z galerii",
                "Widzieć czytelne tagi z sygnałami ostrzegawczymi i kontekstem przepisów",
                "Szukać produktów i składników w katalogu",
                "Zapisywać produkty na My list na tym urządzeniu",
                "Opcjonalne tagi: Kids, Adults, Elderly, Home",
            ],
            "Ważne",
            "LifeX Food to narzędzie informacyjne, nie porada medyczna. Stosuj lokalne przepisy i własny osąd.",
        ),
    ),
    (
        "ru-RU",
        "LifeX: Проверка состава",
        "Сканируйте этикетки—находите запрещённые/ограниченные добавки. Знайте состав.",
        full_template(
            "LifeX Food помогает понять, что в упакованных продуктах—до покупки или подачи.",
            "Сфотографируйте этикетку состава. Мы читаем упаковку, сопоставляем вещества и показываем регуляторные сигналы для вашего рынка (начиная с Индии).",
            "Что можно делать",
            [
                "Сканировать этикетки камерой или из галереи",
                "Видеть понятные теги с сигналами внимания и контекстом норм",
                "Искать продукты и ингредиенты в каталоге",
                "Сохранять упаковки в My list на этом устройстве",
                "Теги: Kids, Adults, Elderly, Home",
            ],
            "Важно",
            "LifeX Food — информационный инструмент, не медицинская консультация. Соблюдайте местные правила и своё суждение.",
        ),
    ),
    (
        "tr-TR",
        "LifeX: İçerik kontrolü",
        "Etiket tarayın—yasak/kısıtlı katkıları görün. Pakette ne var öğrenin.",
        full_template(
            "LifeX Food, paketli gıdalarda ne olduğunu—satın almadan veya sunmadan önce anlamanıza yardımcı olur.",
            "İçerik etiketinin fotoğrafını çekin. Paketi okur, maddeleri eşleştirir ve pazarınız için (Hindistan’dan başlayarak) düzenleyici sinyaller gösteririz.",
            "Neler yapabilirsiniz",
            [
                "Kamera veya galeri ile etiket tara",
                "Uyarı sinyalleri ve düzenleyici bağlamlı etiketler gör",
                "Katalogda ürün ve içerik ara",
                "Bu cihazda My list’e kaydet",
                "İsteğe bağlı etiketler: Kids, Adults, Elderly, Home",
            ],
            "Önemli",
            "LifeX Food bir bilgi aracıdır; tıbbi tavsiye değildir. Yerel kurallara ve kendi kararınıza uyun.",
        ),
    ),
    (
        "ar",
        "LifeX: فحص المكونات",
        "امسح الملصقات—اكتشف المواد المحظورة/المقيّدة. اعرف ما في العبوة.",
        full_template(
            "يساعدك LifeX Food على فهم ما يوجد في الأطعمة المعبأة—قبل الشراء أو التقديم.",
            "التقط صورة لملصق المكونات. نقرأ العبوة ونطابق المواد ونعرض إشارات تنظيمية لسوقك (بدءًا من الهند).",
            "ما يمكنك فعله",
            [
                "مسح الملصقات بالكاميرا أو المعرض",
                "رؤية وسوم واضحة مع إشارات تنبيه وسياق تنظيمي",
                "البحث عن المنتجات والمكونات في الكتالوج",
                "حفظ العبوات في My list على هذا الجهاز",
                "وسوم اختيارية: Kids, Adults, Elderly, Home",
            ],
            "مهم",
            "LifeX Food أداة معلومات وليس نصيحة طبية. التزم باللوائح المحلية وحكمك الخاص.",
        ),
    ),
    (
        "zh-CN",
        "LifeX：食品配料核查",
        "扫描配料表，识别禁用/限用添加剂。看清包装里有什么。",
        full_template(
            "LifeX Food 帮你在购买或食用前，看清包装食品里有什么。",
            "拍摄配料表。我们识别包装、匹配物质，并针对你的市场（从印度起步）展示监管信号——包括禁用与限用添加剂。",
            "你可以做什么",
            [
                "用相机或相册扫描配料表",
                "查看带关注信号与监管语境的清晰标签",
                "在目录中搜索产品与成分",
                "将包装保存到本机 My list",
                "可选标签：Kids、Adults、Elderly、Home",
            ],
            "重要提示",
            "LifeX Food 是信息工具，不构成医疗建议。请遵循当地法规与自身判断。",
        ),
    ),
    (
        "zh-TW",
        "LifeX：食品配料檢查",
        "掃描成分標示，辨識禁用／限用添加物。看清楚包裝裡有什麼。",
        full_template(
            "LifeX Food 協助你在購買或食用前，了解包裝食品的內容。",
            "拍攝成分標示。我們讀取包裝、比對物質，並依你的市場（從印度開始）顯示法規訊號——包括禁用與限用添加物。",
            "你可以做什麼",
            [
                "以相機或相簿掃描標示",
                "查看具警示訊號與法規脈絡的清楚標籤",
                "在目錄搜尋產品與成分",
                "將包裝儲存到此裝置的 My list",
                "可選標籤：Kids、Adults、Elderly、Home",
            ],
            "重要",
            "LifeX Food 是資訊工具，非醫療建議。請遵循當地規範與自身判斷。",
        ),
    ),
    (
        "ja-JP",
        "LifeX: 原材料チェック",
        "表示をスキャンし、禁止・制限成分を確認。パックの中身がわかります。",
        full_template(
            "LifeX Foodは、購入・提供の前に包装食品の中身を把握できるようサポートします。",
            "原材料表示を撮影してください。パッケージを読み取り、物質を照合し、市場向けの規制シグナル（インドから開始）を表示します。",
            "できること",
            [
                "カメラ／ギャラリーで表示をスキャン",
                "注意シグナルと規制コンテキスト付きのタグを確認",
                "カタログで製品・成分を検索",
                "この端末の My list に保存",
                "任意タグ：Kids、Adults、Elderly、Home",
            ],
            "重要",
            "LifeX Foodは情報ツールであり、医療アドバイスではありません。地域の規制とご自身の判断に従ってください。",
        ),
    ),
    (
        "ko-KR",
        "LifeX: 원재료 확인",
        "라벨을 스캔해 금지·제한 성분을 확인하세요. 패키지 내용을 파악하세요.",
        full_template(
            "LifeX Food는 구매하거나 제공하기 전에 포장 식품에 무엇이 들어 있는지 이해하는 데 도움을 줍니다.",
            "원재료 라벨을 촬영하세요. 패키지를 읽고 물질을 매칭한 뒤, 시장(인도부터 시작)에 맞는 규제 신호를 표시합니다.",
            "할 수 있는 일",
            [
                "카메라/갤러리로 라벨 스캔",
                "주의 신호와 규제 맥락이 있는 명확한 태그 확인",
                "카탈로그에서 제품·성분 검색",
                "이 기기의 My list에 저장",
                "선택 태그: Kids, Adults, Elderly, Home",
            ],
            "중요",
            "LifeX Food는 정보 도구이며 의료 조언이 아닙니다. 현지 규정과 본인의 판단을 따르세요.",
        ),
    ),
    (
        "th",
        "LifeX: ตรวจส่วนประกอบ",
        "สแกนฉลาก—พบสารต้องห้าม/จำกัด รู้ว่าในแพ็กมีอะไร",
        full_template(
            "LifeX Food ช่วยให้เข้าใจว่าในอาหารบรรจุภัณฑ์มีอะไร—ก่อนซื้อหรือเสิร์ฟ",
            "ถ่ายรูปฉลากส่วนประกอบ เราอ่านแพ็ก จับคู่สาร และแสดงสัญญาณกำกับดูแลตามตลาดของคุณ (เริ่มจากอินเดีย)",
            "สิ่งที่ทำได้",
            [
                "สแกนฉลากด้วยกล้องหรือแกลเลอรี",
                "ดูแท็กชัดเจนพร้อมสัญญาณเฝ้าระวังและบริบทกฎ",
                "ค้นหาผลิตภัณฑ์และส่วนประกอบในแคตตาล็อก",
                "บันทึกแพ็กไว้ใน My list บนอุปกรณ์นี้",
                "แท็กเสริม: Kids, Adults, Elderly, Home",
            ],
            "สำคัญ",
            "LifeX Food เป็นเครื่องมือข้อมูล ไม่ใช่คำแนะนำทางการแพทย์ ปฏิบัติตามกฎท้องถิ่นและการตัดสินใจของคุณ",
        ),
    ),
    (
        "vi",
        "LifeX: Kiểm tra thành phần",
        "Quét nhãn—phát hiện phụ gia cấm/hạn chế. Biết bên trong có gì.",
        full_template(
            "LifeX Food giúp bạn hiểu thực phẩm đóng gói có gì—trước khi mua hoặc phục vụ.",
            "Chụp nhãn thành phần. Chúng tôi đọc bao bì, khớp chất và hiện tín hiệu quản lý cho thị trường của bạn (bắt đầu từ Ấn Độ).",
            "Bạn có thể",
            [
                "Quét nhãn bằng camera hoặc thư viện",
                "Xem thẻ rõ ràng kèm tín hiệu cảnh báo và ngữ cảnh quy định",
                "Tìm sản phẩm và thành phần trong danh mục",
                "Lưu gói vào My list trên thiết bị này",
                "Thẻ tùy chọn: Kids, Adults, Elderly, Home",
            ],
            "Quan trọng",
            "LifeX Food là công cụ thông tin, không phải tư vấn y tế. Tuân thủ quy định địa phương và phán đoán của bạn.",
        ),
    ),
    (
        "id",
        "LifeX: Cek bahan makanan",
        "Pindai label—temukan bahan terlarang/terbatas. Tahu isi kemasan.",
        full_template(
            "LifeX Food membantu Anda memahami isi makanan kemasan—sebelum membeli atau menyajikan.",
            "Foto label bahan. Kami membaca kemasan, mencocokkan zat, dan menampilkan sinyal regulasi untuk pasar Anda (dimulai dari India).",
            "Yang bisa Anda lakukan",
            [
                "Pindai label dengan kamera atau galeri",
                "Lihat tag jelas dengan sinyal pantauan dan konteks aturan",
                "Cari produk dan bahan di katalog",
                "Simpan kemasan ke My list di perangkat ini",
                "Tag opsional: Kids, Adults, Elderly, Home",
            ],
            "Penting",
            "LifeX Food adalah alat informasi, bukan saran medis. Ikuti aturan lokal dan penilaian Anda sendiri.",
        ),
    ),
    (
        "ms-MY",
        "LifeX: Semak bahan makanan",
        "Imbas label—kesan bahan terlarang/terhad. Ketahui isi bungkusan.",
        full_template(
            "LifeX Food membantu anda faham apa yang ada dalam makanan berbungkus—sebelum membeli atau hidang.",
            "Ambil foto label bahan. Kami baca bungkusan, padankan bahan, dan tunjuk isyarat kawal selia untuk pasaran anda (bermula India).",
            "Apa yang boleh anda buat",
            [
                "Imbas label dengan kamera atau galeri",
                "Lihat tag jelas dengan isyarat amaran dan konteks peraturan",
                "Cari produk dan bahan dalam katalog",
                "Simpan bungkusan ke My list pada peranti ini",
                "Tag pilihan: Kids, Adults, Elderly, Home",
            ],
            "Penting",
            "LifeX Food ialah alat maklumat, bukan nasihat perubatan. Ikuti peraturan tempatan dan pertimbangan anda.",
        ),
    ),
]


def validate(rows: list[tuple[str, str, str, str]]) -> None:
    errors: list[str] = []
    for lang, title, short, full in rows:
        if len(title) > 30:
            errors.append(f"{lang} title {len(title)} > 30: {title!r}")
        if len(short) > 80:
            errors.append(f"{lang} short {len(short)} > 80: {short!r}")
        if len(full) > 4000:
            errors.append(f"{lang} full {len(full)} > 4000")
    if errors:
        raise SystemExit("LENGTH ERRORS:\n" + "\n".join(errors))
    print(f"OK: {len(rows)} locales within Play limits")


def write_csv(path: Path, rows: list[tuple[str, str, str, str]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_ALL)
        w.writerow(["language", "title", "short_description", "full_description"])
        w.writerows(rows)


def write_source_csv(path: Path) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as f:
        w = csv.writer(f, quoting=csv.QUOTE_ALL)
        w.writerow(["key", "text"])
        w.writerow(["title", EN_TITLE])
        w.writerow(["short_description", EN_SHORT])
        w.writerow(["full_description", EN_FULL])


def write_xlsx(path: Path, rows: list[tuple[str, str, str, str]]) -> None:
    if Workbook is None:
        print("openpyxl missing; skip xlsx")
        return
    wb = Workbook()
    ws = wb.active
    ws.title = "Store listings"
    ws.append(
        [
            "language",
            "title",
            "short_description",
            "full_description",
            "title_len",
            "short_len",
        ]
    )
    for cell in ws[1]:
        cell.font = Font(name="Arial", bold=True, size=11)
    for lang, title, short, full in rows:
        ws.append([lang, title, short, full, len(title), len(short)])
        for cell in ws[ws.max_row]:
            cell.font = Font(name="Arial", size=10)
            cell.alignment = Alignment(wrap_text=True, vertical="top")
    ws.column_dimensions["A"].width = 12
    ws.column_dimensions["B"].width = 32
    ws.column_dimensions["C"].width = 52
    ws.column_dimensions["D"].width = 80
    for r in range(2, ws.max_row + 1):
        ws.row_dimensions[r].height = 72
    wb.save(path)


def main() -> None:
    validate(ROWS)
    ROOT.mkdir(parents=True, exist_ok=True)
    csv_path = ROOT / "play-store-listing-translations.csv"
    src_path = ROOT / "play-store-listing-source-for-ai.csv"
    xlsx_path = ROOT / "play-store-listing-translations.xlsx"
    write_csv(csv_path, ROWS)
    write_source_csv(src_path)
    write_xlsx(xlsx_path, ROWS)

    readme = ROOT / "README.md"
    langs = ", ".join(r[0] for r in ROWS)
    readme.write_text(
        f"""# Play Store listing translations

## Files
- `play-store-listing-translations.csv` — pre-translated listings (**{len(ROWS)}** locales, UTF-8 BOM)
- `play-store-listing-translations.xlsx` — same data (easy Google Sheets upload)
- `play-store-listing-source-for-ai.csv` — English source keys if Console AI prefers translating from source

## How to import
1. Play Console → Store listing / Translations → **Import translations with AI**
2. Upload the **CSV** or **XLSX**
3. Review each language (title ≤30, short ≤80) and apply

## Locales
{langs}

## Notes
- Brand **LifeX** kept in titles.
- Spot-check Hindi / Tamil / Chinese / Japanese / Arabic before publish.
- Package name stays `biz.lifex.foodcheck`.
""",
        encoding="utf-8",
    )

    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    for p in (csv_path, src_path, xlsx_path, readme):
        if p.exists():
            shutil.copy2(p, DOWNLOADS / p.name)
            print("wrote", p)
            print("copied", DOWNLOADS / p.name)


if __name__ == "__main__":
    main()
