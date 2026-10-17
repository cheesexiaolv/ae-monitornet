// 生成「无线吞吐终端并入通用终端（WUT）」所需的 ae2wtlib:combine 配方 + 配方书 advancement。
// upgrade_throughput.json（通用终端 + 我们的终端）是手写的、已存在，这里只补齐"从两个终端造出通用终端"
// 的那几条 + 全部 advancement。
// 1.19.2 的 AE2WTLib 不会像 1.21.1 那样自动生成这些配方，附属必须自己发 JSON。
// 用法：node tools/gen_wut_recipes.js
const fs = require('fs');
const path = require('path');

const DATA = path.join(__dirname, '..', 'src', 'main', 'resources', 'data', 'aetm');
const RECIPE_DIR = path.join(DATA, 'recipes', 'wireless_universal_terminal');
const ADV_DIR = path.join(DATA, 'advancements', 'recipes', 'wireless_universal_terminal');

// 本模组注册进 WUT 的无线终端（id 必须与 AE2WtLibHook.TERMINAL_ID 一致）
const OURS = { name: 'throughput', item: 'aetm:wireless_throughput_terminal', key: 'throughput' };

// AE2 / AE2WTLib 自带的无线终端
const OTHERS = [
  { name: 'crafting', item: 'ae2:wireless_crafting_terminal' },
  { name: 'pattern_access', item: 'ae2wtlib:wireless_pattern_access_terminal' },
  { name: 'pattern_encoding', item: 'ae2wtlib:wireless_pattern_encoding_terminal' },
];

// ae2wtlib 的配方序列化器只在装了 AE2WTLib 时才存在，所以外面必须包 forge:conditional。
const conditional = (recipe) => ({
  type: 'forge:conditional',
  recipes: [
    {
      conditions: [{ type: 'forge:mod_loaded', modid: 'ae2wtlib' }],
      recipe,
    },
  ],
});

const advancement = (id, criteria, requirements) => ({
  parent: 'minecraft:recipes/root',
  rewards: { recipes: [`aetm:wireless_universal_terminal/${id}`] },
  criteria: {
    ...criteria,
    has_the_recipe: {
      trigger: 'minecraft:recipe_unlocked',
      conditions: { recipe: `aetm:wireless_universal_terminal/${id}` },
    },
  },
  requirements: [requirements],
});

const invChanged = (key, item) => [
  key,
  { trigger: 'minecraft:inventory_changed', conditions: { items: [{ item }] } },
];

fs.mkdirSync(RECIPE_DIR, { recursive: true });
fs.mkdirSync(ADV_DIR, { recursive: true });

const writeJson = (file, value) => {
  fs.writeFileSync(file, JSON.stringify(value, null, 2) + '\n', 'utf8');
  console.log('wrote', path.relative(path.join(__dirname, '..'), file));
};

let count = 0;

// upgrade（配方本体已在仓库里，这里只补 advancement）
writeJson(
  path.join(ADV_DIR, `upgrade_${OURS.name}.json`),
  advancement(
    `upgrade_${OURS.name}`,
    Object.fromEntries([invChanged(`has_${OURS.key}`, OURS.item)]),
    [`has_${OURS.key}`, 'has_the_recipe'],
  ),
);
count++;

// combine：我们的无线终端 + 另一个无线终端 -> 全新的通用终端
for (const other of OTHERS) {
  const id = `combine_${OURS.key}_${other.name}`;
  writeJson(
    path.join(RECIPE_DIR, `${id}.json`),
    conditional({
      type: 'ae2wtlib:combine',
      terminalA: { item: OURS.item },
      terminalB: { item: other.item },
      terminalAName: OURS.name,
      terminalBName: other.name,
    }),
  );
  writeJson(
    path.join(ADV_DIR, `${id}.json`),
    advancement(
      id,
      Object.fromEntries([
        invChanged(`has_${OURS.key}`, OURS.item),
        invChanged(`has_${other.name}`, other.item),
      ]),
      [`has_${OURS.key}`, `has_${other.name}`, 'has_the_recipe'],
    ),
  );
  count += 2;
}
console.log(`done: ${count} files`);
