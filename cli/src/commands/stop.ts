import {Command, Flags} from '@oclif/core'
import * as _ from 'lodash';
import config from '../config';
import { stopAll, remapYAMLTier } from '../lib/docker';

export default class Stop extends Command {
  static description = 'Shuts down running SourceScape containers.'

  static examples = [
    `$ sourcescape stop`,
  ]

  async run() {
    const {flags} = await this.parse(Stop)

    const flattened = _.flatten((config.services as any[]).map((items: any) => {
      return remapYAMLTier(items);
    }));

    await stopAll(flattened, this.log.bind(this), true, false); // destroy = false

    this.exit(0)
  }
}
