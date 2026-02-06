export type DbSetup = {
  setup_id: number
  short_desc: string
  created_dt: string
  updated_dt: string
}

export type SetupWithProducts = DbSetup & {
  title: string
  imageUrl: string
  product_ids: number[]
  tags: string[]
  tip: string
}

const now = () => new Date().toISOString()

export const setupsData: SetupWithProducts[] = [
  {
    setup_id: 1,
    title: '미니멀 화이트 셋업',
    short_desc: '깔끔한 화이트 톤의 심플한 책상 구성',
    imageUrl: '/minimal-white-desk-setup.jpg',
    product_ids: [1, 5, 8],
    tags: ['미니멀', '화이트', '심플', '재택근무'],
    tip: '화이트와 밝은 톤의 가구로 통일감을 주고, 케이블을 최대한 숨겨 깔끔함을 유지하세요. 조도 조절이 가능한 데스크 조명을 함께 배치하면 눈의 피로를 줄일 수 있습니다.',
    created_dt: now(),
    updated_dt: now(),
  },
  {
    setup_id: 2,
    title: '게이밍 RGB 셋업',
    short_desc: '화려한 RGB 조명의 게이밍 환경',
    imageUrl: '/gaming-rgb-desk-setup.jpg',
    product_ids: [3, 4, 8],
    tags: ['게이밍', 'RGB', '퍼포먼스', '시크'],
    tip: '키보드와 마우스, 모니터 라이트바까지 RGB를 맞춰주면 일관된 분위기가 살아납니다. 선 정리는 케이블 트레이로 잡아주고, 암체어나 장패드로 손목과 허리를 보호하세요.',
    created_dt: now(),
    updated_dt: now(),
  },
  {
    setup_id: 3,
    title: '우드 내추럴 셋업',
    short_desc: '따뜻한 원목 느낌의 자연스러운 공간',
    imageUrl: '/wooden-natural-desk-setup.jpg',
    product_ids: [7, 6, 9],
    tags: ['우드톤', '내추럴', '따뜻한', '휴식'],
    tip: '우드 상판과 톤온톤 소품을 매치하고, 간접조명을 더해 부드러운 분위기를 만드세요. 식물을 더하면 습도와 공기질 개선에 도움됩니다.',
    created_dt: now(),
    updated_dt: now(),
  },
  {
    setup_id: 4,
    title: '프로페셔널 오피스',
    short_desc: '효율적인 업무 환경을 위한 전문가 셋업',
    imageUrl: '/professional-office-desk-setup.jpg',
    product_ids: [1, 5, 10],
    tags: ['오피스', '집중', '모던', '영상회의'],
    tip: '모니터 암과 인체공학 의자로 자세를 잡고, 조용한 톤의 색상으로 시각적 피로를 줄이세요. 화상회의용 웹캠과 조명도 함께 세팅하면 업무 효율이 올라갑니다.',
    created_dt: now(),
    updated_dt: now(),
  },
  {
    setup_id: 5,
    title: '스튜디오 크리에이터',
    short_desc: '콘텐츠 제작을 위한 완벽한 작업 공간',
    imageUrl: '/content-creator-studio-desk.jpg',
    product_ids: [2, 5, 10, 11],
    tags: ['크리에이터', '영상편집', '오디오', '집중'],
    tip: '조명과 음향 장비 배치를 먼저 잡고, 케이블 채널로 책상 아래 정리하세요. 듀얼 모니터와 스피커는 삼각 배치를 해 사운드 밸런스를 맞추면 작업 몰입도가 높아집니다.',
    created_dt: now(),
    updated_dt: now(),
  },
  {
    setup_id: 6,
    title: '컴팩트 원룸 셋업',
    short_desc: '작은 공간을 효율적으로 활용한 구성',
    imageUrl: '/compact-small-desk-setup.jpg',
    product_ids: [7, 8, 9],
    tags: ['원룸', '컴팩트', '수납', '멀티'],
    tip: '벽면 선반과 슬림 가구로 수납을 확보하고, 스탠딩 데스크나 폴딩 체어로 공간을 탄력적으로 쓰세요. 밝은 톤과 작은 조명으로 답답함을 줄여줍니다.',
    created_dt: now(),
    updated_dt: now(),
  },
]
