/* Synthetic prototype data only. Not real kitchens, people, addresses, prices or policies. */
window.PROTOTYPE_DATA = {
  zones: [
    { id: "zone-a", name: { ar: "منطقة تجريبية أ", en: "Test zone A" } },
    { id: "zone-b", name: { ar: "منطقة تجريبية ب", en: "Test zone B" } },
  ],
  kitchens: {
    "k1": { name: { ar: "مطبخ تجريبي ١", en: "Test kitchen 1" }, approval: { ar: "تمت مراجعة التشغيل (ليس ضمان سلامة غذاء)", en: "Operationally reviewed (not a food-safety guarantee)" }, reviews: 12, ratingTenths: 46 },
    "k2": { name: { ar: "مطبخ تجريبي ٢", en: "Test kitchen 2" }, approval: { ar: "تمت مراجعة التشغيل (ليس ضمان سلامة غذاء)", en: "Operationally reviewed (not a food-safety guarantee)" }, reviews: 0, ratingTenths: null },
  },
  meals: [
    { id: "m1", kitchen: "k1", zone: "zone-a", priceMinor: 12000, title: { ar: "كشري بيتي", en: "Home-style koshari" },
      portion: { ar: "طبق واحد ~٤٥٠ جم", en: "One plate, about 450 g" },
      ingredients: { ar: "أرز، عدس، مكرونة، حمص، صلصة طماطم، بصل مقرمش", en: "Rice, lentils, pasta, chickpeas, tomato sauce, crispy onion" },
      allergens: ["GLUTEN"], available: 6 },
    { id: "m2", kitchen: "k1", zone: "zone-a", priceMinor: 15000, title: { ar: "محشي كرنب", en: "Stuffed cabbage" },
      portion: { ar: "١٢ قطعة", en: "12 pieces" },
      ingredients: { ar: "كرنب، أرز، طماطم، أعشاب، زيت", en: "Cabbage, rice, tomato, herbs, oil" }, allergens: ["NONE_DECLARED"], available: 1 },
    { id: "m3", kitchen: "k2", zone: "zone-a", priceMinor: 18000, title: { ar: "ملوخية بالفراخ", en: "Molokhia with chicken" },
      portion: { ar: "طبق + ربع فرخة", en: "Bowl plus quarter chicken" },
      ingredients: { ar: "ملوخية، فراخ، ثوم، كزبرة، مرق", en: "Jute leaves, chicken, garlic, coriander, stock" }, allergens: ["UNKNOWN"], available: 0 },
  ],
  slots: [
    { id: "s1", label: { ar: "اليوم ١:٠٠–٢:٠٠ م", en: "Today 1:00–2:00 PM" } },
    { id: "s2", label: { ar: "اليوم ٧:٠٠–٨:٠٠ م", en: "Today 7:00–8:00 PM" } },
  ],
  address: { ar: "عنوان تجريبي: شارع ١، منطقة تجريبية أ", en: "Synthetic address: Street 1, Test zone A" },
  deliveryFeeMinor: 3000,
  taxMinor: 0,
  cookQueue: [
    { number: "MB-T-1042", items: { ar: "٢ × كشري بيتي", en: "2 × Home-style koshari" }, mode: "DELIVERY", acceptByMin: 11 },
    { number: "MB-T-1043", items: { ar: "١ × محشي كرنب", en: "1 × Stuffed cabbage" }, mode: "PICKUP", acceptByMin: 4 },
  ],
  applications: [
    { id: "app-1", name: { ar: "مطبخ تجريبي ٣", en: "Test kitchen 3" }, submitted: { ar: "منذ ساعتين", en: "2 hours ago" }, docs: 2, zones: 1 },
  ],
  cashExceptions: [
    { number: "MB-T-0991", expectedMinor: 27000, reportedMinor: 25000 },
  ],
};
