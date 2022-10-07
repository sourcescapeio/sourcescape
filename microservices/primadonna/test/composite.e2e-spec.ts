import { createProject } from "@ts-morph/bootstrap";
import { InMemoryFileSystemHost, ts } from "@ts-morph/common";
import { CompositeFileSystem } from "../src/worker/composite.fs";

const TSCONFIG = `
{
  "compilerOptions": {
    "module": "commonjs",
    "declaration": true,
    "removeComments": true,
    "emitDecoratorMetadata": true,
    "experimentalDecorators": true,
    "target": "es2017",
    "sourceMap": true,
    "outDir": "./dist",
    "baseUrl": "./",
    "incremental": true,
    "resolveJsonModule": true,
    "esModuleInterop": true,
    "lib": ["ESNext.String", "es2015"]
  },
  "exclude": ["node_modules", "dist"]
}
`

const PROGRAM = `
class TestService {
  start() {
    console.warn('test');
  }
}


class Test {
  constructor(private testService: TestService) {
  }

  test() {
    this.testService.start()
  }
}
`;

describe('CompositeFileSystem (e2e)', () => {

  // yarn test:e2e -i test/composite.e2e-spec.ts -t 'works'
  it('works', async() => {
    const fs1 = new InMemoryFileSystemHost();
    fs1.writeFile('/data1/projects/p1/tsconfig.json', TSCONFIG)

    const fs2 = new InMemoryFileSystemHost();
    fs2.writeFile('/data2/projects/test.ts', PROGRAM)
    fs2.writeFile('/data2/projects/tsconfig.json', '')

    const composite = new CompositeFileSystem([
      {
        root: '/data1/projects/p1',
        fileSystem: fs1
      },
      {
        root: '/data2/projects/',
        fileSystem: fs2
      }
    ]);

    console.warn(await composite.readFile('/tsconfig.json'))

    const project = await createProject({
      tsConfigFilePath: 'tsconfig.json',
      fileSystem: composite
    });

    const program = project.createProgram();
    const diagnostics = ts.getPreEmitDiagnostics(program);
    const languageService = project.getLanguageService();
    
    console.warn(diagnostics)

    // do compile on this


  });
});
