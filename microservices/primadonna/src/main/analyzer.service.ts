import {
  Injectable,
} from '@nestjs/common';

import { parse } from '@typescript-eslint/typescript-estree';

@Injectable()
export class AnalyzerService {
  analyze(text: string): any {
    return parse(text, { loc: true, range: true, jsx: true });
  }
}
